package org.sofi.deadman.component.writer

import akka.actor._
import akka.pattern.pipe
import org.sofi.deadman.messages.event._
import org.sofi.deadman.messages.query._
import org.sofi.deadman.model._
import scala.concurrent.Future

final class AggregateExpirationWriter(val id: String, val eventLog: ActorRef) extends TaskWriter[AggregateExpiration] {

  // Writer ID
  val writerId = "AggregateExpirationWriter"

  // Query for expired tasks
  override def onCommand: Receive = {
    case q: GetExpirations ⇒
      val _ = AggregateExpiration.select(q.aggregate.getOrElse("")).map { result ⇒
        Tasks(result.map(e ⇒ Task(e.key, e.aggregate, e.entity, e.creation, e.ttl, Seq.empty, e.tags.split(","))))
      } recoverWith noTasks pipeTo sender()
  }

  // Convert events to models and batch. Note: An event handler should never write to the database directly.
  def onEvent = {
    case TaskExpiration(t, exp) ⇒
      cache(AggregateExpiration(t.aggregate, t.entity, t.key, t.ttl, t.ts, exp, t.tags.sorted.mkString(",")))
  }

  // Save an aggregate expiration to C*
  override def save(model: AggregateExpiration): Future[Unit] = model.save
}

object AggregateExpirationWriter {
  def name(id: String): String = s"$id-agg-expiration-writer"
  def props(id: String, eventLog: ActorRef): Props = Props(new AggregateExpirationWriter(id, eventLog))
}