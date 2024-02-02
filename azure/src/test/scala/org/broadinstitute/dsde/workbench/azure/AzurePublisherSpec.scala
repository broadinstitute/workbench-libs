package org.broadinstitute.dsde.workbench.azure

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.mtl.Ask
import com.azure.messaging.servicebus.ServiceBusMessage
import fs2.Stream
import io.circe.generic.auto._
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._
import scala.util.Try

class AzurePublisherSpec extends AnyFlatSpecLike with MockitoSugar with Matchers with BeforeAndAfterEach {

  var mockSenderClient: AzureServiceBusSenderClientWrapper = _
  implicit val logger: ConsoleLogger = new ConsoleLogger(
    "unit_test",
    LogLevel(enableDebug = false, enableTrace = false, enableInfo = true, enableWarn = true)
  )
  implicit val traceIdAsk: Ask[IO, TraceId] = Ask.const[IO, TraceId](TraceId("TRACE-ID"))
  override def beforeEach(): Unit = {
    super.beforeEach()
    mockSenderClient = mock[AzureServiceBusSenderClientWrapper]
  }

  "AzurePublisherInterpreter" should "send a message of a custom type serialized as json using publishOne" in {
    val message = TestMessage("Foo", "Bar")
    val messageCaptor: ArgumentCaptor[ServiceBusMessage] = ArgumentCaptor.forClass(classOf[ServiceBusMessage])

    when(mockSenderClient.sendMessageAsync(messageCaptor.capture())).thenReturn(Mono.empty())

    val res = for {
      _ <- AzurePublisherInterpreter.publisher[IO](mockSenderClient).use { pub =>
        pub.publishOne(message)
      }
    } yield ()

    val testResult = Try(res.unsafeRunSync())

    testResult should be a Symbol("success")

    verify(mockSenderClient, times(1)).sendMessageAsync(any[ServiceBusMessage])

    val capturedMessage: ServiceBusMessage = messageCaptor.getValue
    val messageBody = new String(capturedMessage.getBody.toString)
    messageBody shouldEqual """{"name":"Foo","description":"Bar"}"""

  }

  "AzurePublisherInterpreter" should "send message with attributes if they are set" in {
    val message = TestMessage("Foo", "Bar")
    val messageCaptor: ArgumentCaptor[ServiceBusMessage] = ArgumentCaptor.forClass(classOf[ServiceBusMessage])
    val attributes = Map("foo" -> "bar")
    when(mockSenderClient.sendMessageAsync(messageCaptor.capture())).thenReturn(Mono.empty())

    val res = for {
      _ <- AzurePublisherInterpreter.publisher[IO](mockSenderClient).use { pub =>
        pub.publishOne(message, attributes)
      }
    } yield ()

    val testResult = Try(res.unsafeRunSync())

    testResult should be a Symbol("success")

    verify(mockSenderClient, times(1)).sendMessageAsync(any[ServiceBusMessage])

    val capturedMessage: ServiceBusMessage = messageCaptor.getValue
    capturedMessage.getApplicationProperties.get("foo") shouldEqual "bar"
  }
  "AzurePublisherInterpreter" should "send message correlation id with the trace id when provided using publishOne" in {
    val message = TestMessage("Foo", "Bar")
    val messageCaptor: ArgumentCaptor[ServiceBusMessage] = ArgumentCaptor.forClass(classOf[ServiceBusMessage])

    when(mockSenderClient.sendMessageAsync(messageCaptor.capture())).thenReturn(Mono.empty())

    val res = for {
      _ <- AzurePublisherInterpreter.publisher[IO](mockSenderClient).use { pub =>
        pub.publishOne(message)
      }
    } yield ()

    val testResult = Try(res.unsafeRunSync())

    testResult should be a Symbol("success")

    verify(mockSenderClient, times(1)).sendMessageAsync(any[ServiceBusMessage])

    val capturedMessage: ServiceBusMessage = messageCaptor.getValue
    val correlationId = capturedMessage.getCorrelationId
    correlationId shouldEqual traceIdAsk.ask.unsafeRunSync().asString
  }

  "AzurePublisherInterpreter" should "send multiple messages via the client using publish" in {
    val messages = List(TestMessage("Foo", "Bar"), TestMessage("Bar", "Foo"))
    when(mockSenderClient.sendMessageAsync(any[ServiceBusMessage])).thenReturn(Mono.empty())

    val stream = Stream.emits[IO, TestMessage](messages)
    val publisher = AzurePublisherInterpreter.publisher[IO](mockSenderClient)

    val res = publisher.use { pub =>
      stream.through(pub.publish).compile.drain
    }

    val testResult = Try(res.unsafeRunSync())

    testResult should be a Symbol("success")

    // Verify that sendMessageAsync was called for each message
    verify(mockSenderClient, times(messages.length)).sendMessageAsync(any[ServiceBusMessage])
  }

  "AzurePublisherInterpreter" should "send a list of string messages using publishString" in {
    val messages = List("Foo", "Bar")
    val messageCaptor: ArgumentCaptor[ServiceBusMessage] = ArgumentCaptor.forClass(classOf[ServiceBusMessage])

    when(mockSenderClient.sendMessageAsync(messageCaptor.capture())).thenReturn(Mono.empty())

    val stream = Stream.emits[IO, String](messages)
    val publisher = AzurePublisherInterpreter.publisher[IO](mockSenderClient)

    val res = publisher.use { pub =>
      stream.through(pub.publishString).compile.drain
    }

    val testResult = Try(res.unsafeRunSync())

    testResult should be a Symbol("success")

    verify(mockSenderClient, times(messages.length)).sendMessageAsync(any[ServiceBusMessage])

    val capturedMessages = messageCaptor.getAllValues
    val capturedBodies = capturedMessages.asScala.map { msg =>
      new String(msg.getBody.toString)
    }

    capturedBodies should contain theSameElementsAs messages
  }

}

case class TestMessage(name: String, description: String)
