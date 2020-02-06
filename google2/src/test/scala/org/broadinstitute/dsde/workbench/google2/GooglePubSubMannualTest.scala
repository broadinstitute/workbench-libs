package org.broadinstitute.dsde.workbench.google2

import cats.effect.IO
import com.google.pubsub.v1.ProjectTopicName
import fs2.concurrent.InspectableQueue
import fs2.{Pipe, Stream}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Decoder

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object GooglePubSubMannualTest {
  implicit val cs = IO.contextShift(global)
  implicit val t = IO.timer(global)
  implicit def logger = Slf4jLogger.getLogger[IO]
  val projectTopicName = ProjectTopicName.of("broad-dsde-dev", "leonardo-pubsub")

  val path = "/Users/qi/workspace/leonardo/config/leonardo-account.json"
  val printPipe: Pipe[IO, Event[Messagee], Unit] = in => in.evalMap(s => IO(println("receiving "+s.toString)))

  def test() = {
    val config = PublisherConfig(path, projectTopicName, org.broadinstitute.dsde.workbench.google2.GoogleTopicAdminInterpreter.defaultRetryConfig)
    val pub = GooglePublisher.resource[IO](config)
    pub.use(x => (Stream.eval(IO.pure("yes")) through x.publish).compile.drain)
  }

  implicit val msgDecoder: Decoder[Messagee] = Decoder.forProduct1("msg")(Messagee)
  def subscriber() = {
    val config = SubscriberConfig(path, projectTopicName, 1 minute, None)
    for {
      queue <- InspectableQueue.bounded[IO, Event[Messagee]](100)
      sub = GoogleSubscriber.resource[IO, Messagee](config, queue)
      _ <- sub.use {
        s =>
          val stream = Stream(
            Stream.eval(s.start),
            queue.dequeue through printPipe
          ).parJoin(2)
          stream.compile.drain
      }
    } yield ()
  }
}

final case class Messagee(msg: String)