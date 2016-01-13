package traffic.actors

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import play.api.libs.json.Json
import traffic.brokers.MessageBroker
import traffic.model.{CelltowerEvent, Celltower}

class CelltowerEventHandler(celltower: Celltower, template: CelltowerTemplate, broker: MessageBroker) extends Actor with ActorLogging {

    import CelltowerEventHandler._

    var counter = 0

    def emitEvent(bearerId: UUID) = {

        val metrics = template.metrics.map { mt =>
            (mt.name, mt.dist.sample())
        }.toMap

        val celltowerEvent = CelltowerEvent(celltower, bearerId.toString, metrics)
        val message = Json.stringify(Json.toJson(celltowerEvent))

        broker.send("celltower-topic", message)

        counter = counter + 1
    }

    override def receive: Receive = {
        case EmitEvent(bearerId) => emitEvent(bearerId)
    }
}

object CelltowerEventHandler {
    def props(celltower: Celltower, template: CelltowerTemplate, broker: MessageBroker) =
        Props(new CelltowerEventHandler(celltower, template, broker))
    case class EmitEvent(bearerId: UUID)

}

