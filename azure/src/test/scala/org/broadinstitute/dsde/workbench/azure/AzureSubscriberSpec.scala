package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.azure.core.util.BinaryData
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceivedMessageContext}
import io.circe.generic.auto._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import org.mockito.Mockito.{times, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success, Try}

class AzureSubscriberSpec extends AnyFlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterEach {
  var mockReceiverClient: AzureServiceBusReceiverClientWrapper = _
  override def beforeEach(): Unit = {
    super.beforeEach()
    mockReceiverClient = mock[AzureServiceBusReceiverClientWrapper]
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

  "AzureEventMessageHandlerInterpreter" should " decode string message and send it to the queue" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")

    val queue = Queue.unbounded[IO, AzureEvent[String]].unsafeRunSync()

    val runHandler = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureEventMessageHandlerInterpreter(AzureEventMessageDecoder.stringDecoder, queue, dispatcher)
    } yield handler

    runHandler
      .use { handler =>
        IO(handler.handleMessage(receivedMessage))
      }
      .unsafeRunSync()

    assertMessageIsQueued[String](receivedMessage, queue, "test")
  }

  "AzureEventMessageHandlerInterpreter" should " decode json message and send it to the queue" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo","description":"Bar"}""")

    val queue = Queue.unbounded[IO, AzureEvent[TestMessage]].unsafeRunSync()

    val runHandler = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureEventMessageHandlerInterpreter(AzureEventMessageDecoder.jsonDecoder[TestMessage],
                                                    queue,
                                                    dispatcher
      )
    } yield handler

    runHandler
      .use { handler =>
        IO(handler.handleMessage(receivedMessage))
      }
      .unsafeRunSync()

    assertMessageIsQueued(receivedMessage, queue, TestMessage("Foo", "Bar"))
  }

  "AzureEventMessageHandlerInterpreter" should " decode json multiple messages and send them to the queue" in {
    val receivedMessage1 = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo1","description":"Bar1"}""")
    val receivedMessage2 = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo2","description":"Bar2"}""")

    val queue = Queue.unbounded[IO, AzureEvent[TestMessage]].unsafeRunSync()

    val runHandler = for {
      dispatcher <- cats.effect.std.Dispatcher.sequential[IO]
      handler = AzureEventMessageHandlerInterpreter(AzureEventMessageDecoder.jsonDecoder[TestMessage],
                                                    queue,
                                                    dispatcher
      )
    } yield handler

    runHandler
      .use { handler =>
        IO(handler.handleMessage(receivedMessage1)) *>
          IO(handler.handleMessage(receivedMessage2))
      }
      .unsafeRunSync()

    assertMessageIsQueued(receivedMessage1, queue, TestMessage("Foo1", "Bar1"))
    assertMessageIsQueued(receivedMessage2, queue, TestMessage("Foo2", "Bar2"))
  }

  private def assertMessageIsQueued[MessageType](receivedMessage: ServiceBusReceivedMessage,
                                                 queue: Queue[IO, AzureEvent[MessageType]],
                                                 expectedMessage: MessageType
  ) = {
    val testResult = for {
      msgOrTimeout <- IO.race(queue.take, IO.sleep(3.seconds))
    } yield msgOrTimeout

    Try(testResult.unsafeRunSync()) match {
      case Success(Left(message)) =>
        message shouldEqual AzureEvent(expectedMessage,
                                       None,
                                       ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage)
        )
      case Success(Right(_))  => fail("Timeout reached without receiving a message")
      case Failure(exception) => fail(exception)
    }
  }

  // Okay, using the last resort approach to create a received message: reflection
  // This is because ServiceBusReceivedMessage is a final class and cannot be mocked, even when mockito is configured to mock final classes
  // and the SDK does not provide a factory to create a received message
  def createServiceBusReceivedMessageUsingReflection(body: String): ServiceBusReceivedMessage = {
    val clazz = classOf[ServiceBusReceivedMessage]
    val constructor = clazz.getDeclaredConstructor(classOf[BinaryData])
    constructor.setAccessible(true)
    constructor.newInstance(BinaryData.fromString(body))
  }

  def createServiceBusReceivedMessageContextUsingReflection(
    receivedMessage: ServiceBusReceivedMessage
  ): ServiceBusReceivedMessageContext = {
    val clazz = classOf[ServiceBusReceivedMessageContext]
    val constructor = clazz.getDeclaredConstructor(classOf[ServiceBusReceivedMessage])
    constructor.setAccessible(true)
    constructor.newInstance(receivedMessage)
  }
}
