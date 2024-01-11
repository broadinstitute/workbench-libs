package org.broadinstitute.dsde.workbench.azure

import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.mtl.Ask
import com.azure.core.util.BinaryData
import com.azure.messaging.servicebus.ServiceBusReceivedMessage
import com.google.protobuf.Timestamp
import org.broadinstitute.dsde.workbench.model.TraceId
import org.broadinstitute.dsde.workbench.util2.{ConsoleLogger, LogLevel}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.mockito.MockitoSugar
import reactor.core.publisher.Flux

import scala.util.Try

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

  // Okay, using the last resort approach to create a received message: reflection
  // This is because ServiceBusReceivedMessage is a final class and cannot be mocked, even when mockito is configured to mock final classes
  // and the SDK does not provide a factory to create a received message
  def createServiceBusReceivedMessageUsingReflection(body: String): ServiceBusReceivedMessage = {
    val clazz = classOf[ServiceBusReceivedMessage]
    val constructor = clazz.getDeclaredConstructor(classOf[BinaryData])
    constructor.setAccessible(true)
    constructor.newInstance(BinaryData.fromString(body))
  }

  "AzureSubscriber" should "receive a string message successfully" in {
    val receivedMessage = createServiceBusReceivedMessageUsingReflection("test")

    // Mock the messages received from the service bus
    val messagesFlux = Flux.just(receivedMessage)
    when(mockReceiverClient.receiveMessagesAsync()).thenReturn(messagesFlux)

    val res = for {
      queue <- Queue.unbounded[IO, AzureEvent[String]]
      _ <- AzureSubscriberInterpreter.stringSubscriber(mockReceiverClient, queue).use(sub => sub.start)
      a <- queue.take
      _ = println(a)
    } yield () // IO.race(queue.take, IO.sleep(3.seconds))

    res.unsafeRunSync()

    // queue.offer(AzureEvent("test",None,Timestamp.getDefaultInstance)).unsafeRunSync()

    // val x = queue.take.unsafeRunSync()

    // val res1 =  IO.race(queue.take, IO.sleep(3.seconds)).unsafeRunSync()

//    res match {
//      case Left(message) => println(s"Received message: $message")
//      case Right(_)      => println("Timeout reached without receiving a message")
//    }

    // Run the test and verify the results
//    val testResult = Try {
//      res.unsafeRunSync() match {
//        case Left(message) => println(s"Received message: $message")
//        case Right(_)      => println("Timeout reached without receiving a message")
//      }
//    }
    // testResult should be a Symbol("success")
    // res.unsafeRunSync() should be a Symbol("success")
  }

  "AzureSubscriber" should "simple dequeue" in {

    val items = Flux.just("hello", "world", "!")

    val queue: Queue[IO, String] = Queue.unbounded[IO, String].unsafeRunSync()

    items.subscribe((item: String) => queue.offer(item).unsafeRunAsync(c => println(s"Completed: $c")),
                    (error: Throwable) => println(s"Error: $error")
    )

    val program: IO[Unit] = for {

      // Read the event from the queue
      dequeuedEvent <- queue.take

      // Do something with the dequeued event
      _ = println(dequeuedEvent)

    } yield ()

    // Run the program
    program.unsafeRunSync()
  }
}
