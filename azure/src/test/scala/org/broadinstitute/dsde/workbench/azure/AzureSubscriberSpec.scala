package org.broadinstitute.dsde.workbench.azure

import cats.effect._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.azure.core.util.BinaryData
import com.azure.messaging.servicebus.{ServiceBusReceivedMessage, ServiceBusReceivedMessageContext}
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{withLogging, ConsoleLogger, LogLevel}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar
import io.circe.generic.auto._
import org.mockito.Mockito.{times, verify}

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

    val testResult = for {
      msgOrTimeout <- IO.race(queue.take, IO.sleep(3.seconds))
    } yield msgOrTimeout

    Try(testResult.unsafeRunSync()) match {
      case Success(Left(message)) =>
        message shouldEqual AzureEvent("test", None, ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage))
      case Success(Right(_))  => fail("Timeout reached without receiving a message")
      case Failure(exception) => fail(exception)
    }
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

    val testResult = for {
      msgOrTimeout <- IO.race(queue.take, IO.sleep(3.seconds))
    } yield msgOrTimeout

    Try(testResult.unsafeRunSync()) match {
      case Success(Left(message)) =>
        message shouldEqual AzureEvent(TestMessage("Foo", "Bar"),
                                       None,
                                       ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage)
        )
      case Success(Right(_))  => fail("Timeout reached without receiving a message")
      case Failure(exception) => fail(exception)
    }
  }

  "AzureEventMessageHandlerInterpreter" should " decode json multiple messages and send them to the queue" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo","description":"Bar"}""")
    val receivedMessage2 = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo","description":"Bar"}""")

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

    runHandler
      .use { handler =>
        IO(handler.handleMessage(receivedMessage2))
      }
      .unsafeRunSync()

    assertMessageIsReceived(receivedMessage, queue, TestMessage("Foo", "Bar"))
    assertMessageIsReceived(receivedMessage2, queue, TestMessage("Foo", "Bar"))
  }

//    val queue = Queue[IO, AzureEvent[String]].unsafeRunSync()
//    val handler = AzureEventMessageHandlerInterpreter(AzureEventMessageDecoder.stringDecoder, queue)
//
//    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")
//    val context = createServiceBusReceivedMessageContextUsingReflection(receivedMessage)
//
//    handler.handleMessage(receivedMessage)
//
//    verify(queue, times(1)).offer(AzureEvent("test", None, ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage)))
//  }
//  "AzureSubscriberInterpreter" should "receive a string message successfully" in {
//    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")
//
//    val messagesFlux = Flux.just(receivedMessage)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//
//    val res = for {
//      queue <- Queue.unbounded[IO, AzureEvent[String]]
//      _ <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue).use(sub => sub.start)
//      // Wait for a message or timeout after 3 seconds
//      messageOrTimeout <- IO.race(queue.take, IO.sleep(3.seconds))
//    } yield messageOrTimeout
//
//    val testResult = Try(res.unsafeRunSync())
//
//    testResult match {
//      case Success(Left(message)) =>
//        message shouldEqual AzureEvent("test", None, ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage))
//      case Success(Right(_))  => fail("Timeout reached without receiving a message")
//      case Failure(exception) => fail(exception)
//    }
//  }
//
//  "AzureSubscriberInterpreter" should "receive a string message and publish it to stream successfully" in {
//    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")
//
//    val messagesFlux = Flux.just(receivedMessage)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//
//    val res = for {
//      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[String]])
//      subs <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue)
//      _ <- Resource.eval(subs.start)
//      resultList <- Resource.eval(
//        subs.messages
//          .take(1)
//          .compile
//          .toList
//          .timeout(3.seconds)
//          .attempt
//      )
//    } yield resultList
//
//    val result = res.use(_.pure[IO]).unsafeRunSync()
//
//    result match {
//      case Right(list) =>
//        list should have size 1
//      case Left(ex) =>
//        fail(s"An exception occurred: ${ex.getMessage}")
//    }
//  }
//
//  "AzureSubscriberInterpreter" should "receive multiple string messages and publish them to stream successfully" in {
//    val msg1 = createServiceBusReceivedMessageUsingReflection("msg1")
//    val msg2 = createServiceBusReceivedMessageUsingReflection("msg2")
//    val msg3 = createServiceBusReceivedMessageUsingReflection("msg3")
//
//    val messagesFlux = Flux.just(msg1, msg2, msg3)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//
//    val res = for {
//      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[String]])
//      subs <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue)
//      _ <- Resource.eval(subs.start)
//      resultList <- Resource.eval(
//        subs.messages
//          .take(3)
//          .compile
//          .toList
//          .timeout(3.seconds)
//          .attempt
//      )
//    } yield resultList
//
//    val result = res.use(_.pure[IO]).unsafeRunSync()
//
//    result match {
//      case Right(list) =>
//        list should have size 3
//      case Left(ex) =>
//        fail(s"An exception occurred: ${ex.getMessage}")
//    }
//  }
//
//  "AzureSubscriberInterpreter" should "receive a json message and publish them to stream successfully" in {
//    val msg1 = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo","description":"Bar"}""")
//
//    val messagesFlux = Flux.just(msg1)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
//
//    val res = for {
//      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[TestMessage]])
//      subs <- AzureSubscriberInterpreter.subscriber[IO, TestMessage](mockReceiverClient, queue)
//      _ <- Resource.eval(subs.start)
//      resultList <- Resource.eval(
//        subs.messages
//          .take(1)
//          .compile
//          .toList
//          .timeout(3.seconds)
//          .attempt
//      )
//    } yield resultList
//
//    val result = res.use(_.pure[IO]).unsafeRunSync()
//
//    result match {
//      case Right(list) =>
//        list should have size 1
//      case Left(ex) =>
//        fail(s"An exception occurred: ${ex.getMessage}")
//    }
//  }

  private def assertMessageIsReceived(receivedMessage: ServiceBusReceivedMessage,
                                      queue: Queue[IO, AzureEvent[TestMessage]],
                                      expectedMessage: TestMessage
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
