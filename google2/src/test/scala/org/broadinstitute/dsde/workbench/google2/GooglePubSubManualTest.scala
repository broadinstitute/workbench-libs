package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import com.google.pubsub.v1.ProjectTopicName
import fs2.concurrent.InspectableQueue
import fs2.{Pipe, Stream}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder
import cats.syntax.all._
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object GooglePubSubManualTest {
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit def logger = Slf4jLogger.getLogger[IO]

  // NOTE: Update the next 2 lines to your own data

  val projectTopicName = ProjectTopicName.of("your google project", "your topic name")
  val path = "your service account path"

  val printPipe: Pipe[IO, Event[Message], Unit] = in =>
    in.evalMap(s => IO(println("processed " + s)) >> IO(s.consumer.ack()))

  /**
   * How to use this:
   * 1. sbt "project workbenchGoogle2" test:console
   * 2. val res = org.broadinstitute.dsde.workbench.google2.GooglePubSubManualTest.publish()
   * 3. res.unsafeRunSync
   *
   * You can now see messages being published
   */
  def publish() = {
    val config = PublisherConfig(
      path,
      projectTopicName
    )
    val pub = GooglePublisher.resource[IO](config)
    pub.use(x => (Stream.eval(IO.pure("yes")) through x.publish).compile.drain)
  }

  implicit val msgDecoder: Decoder[Message] = Decoder.forProduct1("msg")(Message)

  /**
   * How to use this:
   * 1. sbt "project workbenchGoogle2" test:console
   * 2. val res = org.broadinstitute.dsde.workbench.google2.GooglePubSubManualTest.subscriber()
   * 3. res.unsafeRunSync
   *
   * You can now publish messages in console and watch messages being printed out
   */
  def subscriber() = {
    val config = SubscriberConfig(path, projectTopicName, None, 1 minute, None, None, None)
    for {
      queue <- InspectableQueue.bounded[IO, Event[Message]](100)
      sub = GoogleSubscriber.resource[IO, Message](config, queue)
      _ <- sub.use { s =>
        val stream = Stream(
          Stream.eval(s.start),
          queue.dequeue through printPipe
        ).parJoin(2)
        stream.compile.drain
      }
    } yield ()
  }
}

final case class Message(msg: String)
