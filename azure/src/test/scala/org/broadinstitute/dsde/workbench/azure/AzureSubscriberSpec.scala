package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.azure.core.util.BinaryData
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceivedMessageContext}
import io.circe.generic.auto._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.messaging.{AckHandler, ReceivedMessage}
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success, Try}

class AzureSubscriberSpec extends AnyFlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterEach {
  var mockReceiverClient: AzureServiceBusReceiverClientWrapper = _
  var mockAckHandler: AckHandler = _
  override def beforeEach(): Unit = {
    super.beforeEach()
    mockReceiverClient = mock[AzureServiceBusReceiverClientWrapper]
    mockAckHandler = mock[AckHandler]
  }

  implicit val logger: ConsoleLogger = new ConsoleLogger(
    "unit_test",
    LogLevel(enableDebug = false, enableTrace = false, enableInfo = true, enableWarn = true)
  )
  implicit val traceIdAsk: Ask[IO, TraceId] = Ask.const[IO, TraceId](TraceId("TRACE-ID"))

  "AzureSubscriberInterpreter" should "start the processor" in {
    val subscriber = new AzureSubscriberInterpreter[IO, String](mockReceiverClient, null)
    subscriber.start.unsafeRunSync()
    verify(mockReceiverClient, times(1)).startProcessor()
  }

  "AzureSubscriberInterpreter" should "stop the processor" in {
    val subscriber = new AzureSubscriberInterpreter[IO, String](mockReceiverClient, null)
    subscriber.stop.unsafeRunSync()
    verify(mockReceiverClient, times(1)).stopProcessor()
  }

  "AzureReceivedMessageHandlerInterpreter" should " decode string message and send it to the queue" in {
    val receivedMessage = createMockServiceBusReceivedMessage("test")
    val messageContext = createMessageContextMock(receivedMessage)

    val queue = Queue.unbounded[IO, ReceivedMessage[String]].unsafeRunSync()

    val handlerResource = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureReceivedMessageHandlerInterpreter(AzureReceivedMessageDecoder.stringDecoder, queue, dispatcher)
    } yield handler

    handlerResource
      .use { handler =>
        IO(handler.handleMessage(messageContext))
      }
      .unsafeRunSync()

    assertMessageIsQueued[String](queue, "test")
  }

  "AzureReceivedMessageHandlerInterpreter" should " decode json message and send it to the queue" in {
    val receivedMessage = createMockServiceBusReceivedMessage("""{"name":"Foo","description":"Bar"}""")
    val messageContext = createMessageContextMock(receivedMessage)
    val queue = Queue.unbounded[IO, ReceivedMessage[TestMessage]].unsafeRunSync()

    val handlerResource = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureReceivedMessageHandlerInterpreter(AzureReceivedMessageDecoder.jsonDecoder[TestMessage],
                                                       queue,
                                                       dispatcher
      )
    } yield handler

    handlerResource
      .use { handler =>
        IO(handler.handleMessage(messageContext))
      }
      .unsafeRunSync()

    assertMessageIsQueued(queue, TestMessage("Foo", "Bar"))
  }

  "AzureReceivedMessageHandlerInterpreter" should " abandon message when handler fails " in {

    val messageContext = mock[ServiceBusReceivedMessageContext]
    when(messageContext.getMessage).thenThrow(new RuntimeException("test exception"))

    val queue = Queue.unbounded[IO, ReceivedMessage[TestMessage]].unsafeRunSync()

    val handlerResource = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureReceivedMessageHandlerInterpreter(AzureReceivedMessageDecoder.jsonDecoder[TestMessage],
                                                       queue,
                                                       dispatcher
      )
    } yield handler

    val runHandler = handlerResource.use { handler =>
      IO(handler.handleMessage(messageContext))
    }

    val testResult = runHandler.unsafeRunSync()

    testResult match {
      case Success(_) => fail("Expected exception")
      case Failure(e) => e.getMessage shouldEqual "test exception"
    }

    verify(messageContext, times(1)).abandon()
  }

  "AzureReceivedMessageHandlerInterpreter" should " decode json multiple messages and send them to the queue" in {
    val receivedMessage1 = createMockServiceBusReceivedMessage("""{"name":"Foo1","description":"Bar1"}""")
    val receivedMessage2 = createMockServiceBusReceivedMessage("""{"name":"Foo2","description":"Bar2"}""")
    val msgContext1 = createMessageContextMock(receivedMessage1)
    val msgContext2 = createMessageContextMock(receivedMessage2)

    val queue = Queue.unbounded[IO, ReceivedMessage[TestMessage]].unsafeRunSync()

    val handlerResource = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureReceivedMessageHandlerInterpreter(AzureReceivedMessageDecoder.jsonDecoder[TestMessage],
                                                       queue,
                                                       dispatcher
      )
    } yield handler

    handlerResource
      .use { handler =>
        IO(handler.handleMessage(msgContext1)) *>
          IO(handler.handleMessage(msgContext2))
      }
      .unsafeRunSync()

    assertMessageIsQueued(queue, TestMessage("Foo1", "Bar1"))
    assertMessageIsQueued(queue, TestMessage("Foo2", "Bar2"))
  }

  private def assertMessageIsQueued[MessageType](queue: Queue[IO, ReceivedMessage[MessageType]],
                                                 expectedMessage: MessageType
  ) = {
    val testResult = for {
      msgOrTimeout <- IO.race(queue.take, IO.sleep(3.seconds))
    } yield msgOrTimeout

    Try(testResult.unsafeRunSync()) match {
      case Success(Left(message)) =>
        message.msg shouldEqual expectedMessage
      case Success(Right(_))  => fail("Timeout reached without receiving a message")
      case Failure(exception) => fail(exception)
    }
  }

  def createMockServiceBusReceivedMessage(body: String): ServiceBusReceivedMessage = {
    val mockMsg = mock[ServiceBusReceivedMessage]
    when(mockMsg.getBody).thenReturn(BinaryData.fromString(body))
    mockMsg
  }

  def createMessageContextMock(
    receivedMessage: ServiceBusReceivedMessage
  ): ServiceBusReceivedMessageContext = {
    val mockCtx = mock[ServiceBusReceivedMessageContext]
    when(mockCtx.getMessage).thenReturn(receivedMessage)
    mockCtx
  }
}
