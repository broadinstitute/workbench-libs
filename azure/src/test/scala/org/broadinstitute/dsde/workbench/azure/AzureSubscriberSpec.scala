package org.broadinstitute.dsde.workbench.azure

import cats.effect.{IO, Resource}
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.implicits.catsSyntaxApplicativeId
import cats.mtl.Ask
import com.azure.core.util.BinaryData
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar
import reactor.core.publisher.Flux
import io.circe.generic.auto._

import scala.util.{Failure, Success, Try}

class AzureSubscriberSpec extends AnyFlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterEach {
  var mockReceiverClient: AzureServiceBusReceiverClientWrapper = _
  implicit val logger: ConsoleLogger = new ConsoleLogger(
    "unit_test",
    LogLevel(enableDebug = false, enableTrace = false, enableInfo = true, enableWarn = true)
  )
  implicit val traceIdAsk: Ask[IO, TraceId] = Ask.const[IO, TraceId](TraceId("TRACE-ID"))
  override def beforeEach(): Unit = {
    super.beforeEach()
    mockReceiverClient = mock[AzureServiceBusReceiverClientWrapper]
  }

  "AzureSubscriberInterpreter" should "receive a string message successfully" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")

    val messagesFlux = Flux.just(receivedMessage)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Queue.unbounded[IO, AzureEvent[String]]
      _ <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue).use(sub => sub.start)
      messageOpt <- queue.tryTake
    } yield messageOpt

    val testResult = res.unsafeRunSync()

    testResult shouldEqual Some(
      AzureEvent("test", None, ServiceBusMessageUtils.getEnqueuedTimeOrDefault(receivedMessage))
    )
  }

  it should "raise error properly" in {
    val messagesFlux = Flux.error[ServiceBusReceivedMessage](new RuntimeException("shit"))
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Queue.unbounded[IO, AzureEvent[String]]
      _ <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue).use(sub => sub.start)
      messageOpt <- queue.tryTake
    } yield messageOpt

    val testResult = res.unsafeRunSync()

    testResult shouldEqual None
  }

  "AzureSubscriberInterpreter" should "receive a string message and publish it to stream successfully" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")

    val messagesFlux = Flux.just(receivedMessage)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[String]])
      subs <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue)
      _ <- Resource.eval(subs.start)
      resultList <- Resource.eval(
        subs.messages
          .take(1)
          .compile
          .toList
          .timeout(3.seconds)
          .attempt
      )
    } yield resultList

    val result = res.use(_.pure[IO]).unsafeRunSync()

    result match {
      case Right(list) =>
        list should have size 1
      case Left(ex) =>
        fail(s"An exception occurred: ${ex.getMessage}")
    }
  }

  "AzureSubscriberInterpreter" should "receive multiple string messages and publish them to stream successfully" in {
    val msg1 = createServiceBusReceivedMessageUsingReflection("msg1")
    val msg2 = createServiceBusReceivedMessageUsingReflection("msg2")
    val msg3 = createServiceBusReceivedMessageUsingReflection("msg3")

    val messagesFlux = Flux.just(msg1, msg2, msg3)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[String]])
      subs <- AzureSubscriberInterpreter.stringSubscriber[IO](mockReceiverClient, queue)
      _ <- Resource.eval(subs.start)
      resultList <- Resource.eval(
        subs.messages
          .take(3)
          .compile
          .toList
          .timeout(3.seconds)
          .attempt
      )
    } yield resultList

    val result = res.use(_.pure[IO]).unsafeRunSync()

    result match {
      case Right(list) =>
        list should have size 3
      case Left(ex) =>
        fail(s"An exception occurred: ${ex.getMessage}")
    }
  }

  "AzureSubscriberInterpreter" should "receive a json message and publish them to stream successfully" in {
    val msg1 = createServiceBusReceivedMessageUsingReflection("""{"name":"Foo","description":"Bar"}""")

    val messagesFlux = Flux.just(msg1)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Resource.eval(Queue.unbounded[IO, AzureEvent[TestMessage]])
      subs <- AzureSubscriberInterpreter.subscriber[IO, TestMessage](mockReceiverClient, queue)
      _ <- Resource.eval(subs.start)
      resultList <- Resource.eval(
        subs.messages
          .take(1)
          .compile
          .toList
          .timeout(3.seconds)
          .attempt
      )
    } yield resultList

    val result = res.use(_.pure[IO]).unsafeRunSync()

    result match {
      case Right(list) =>
        list should have size 1
      case Left(ex) =>
        fail(s"An exception occurred: ${ex.getMessage}")
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
}
