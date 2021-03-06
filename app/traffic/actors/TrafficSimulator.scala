package traffic.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.routing.{Broadcast, FromConfig}
import play.api.libs.json.{JsValue, Json}
import traffic.actors.TripHandler.{StartTrip, StopTrip}
import traffic.protocol.{RequestEvent, RequestUpdateEvent}

class TrafficSimulator() extends Actor with ActorLogging {

    import TrafficSimulator._

    val mediator = DistributedPubSub(context.system).mediator
    mediator ! Subscribe("request-topic", self)

    var currentRequest: RequestEvent = _

    /*
    since the TrafficSimulator is a singleton, the router pool can only be created once
     */
    val routerProps = FromConfig.props(Props[TripHandler])
    val router = context.actorOf(routerProps, "TripRouter")

    def initState(json: JsValue) = {
        currentRequest = (json \ "request").as[RequestEvent]
        log.debug("initializing state as: {}", currentRequest.toString)
    }

    def startSimulation(json: JsValue) = {

        initState(json)

        // stop running simulation before starting a new one
        stopSimulation()

        log.info("starting simulation")

        log.info("starting simulation")
        for (i <- 1 to currentRequest.numTrips) {
            router ! StartTrip(currentRequest)
        }
    }

    def stopSimulation() = {
        log.info("stopping simulation")
        router ! Broadcast(StopTrip)
    }

    def updateSimulation(json: JsValue) = {
        val r = (json \ "request").as[RequestUpdateEvent]
        log.info("request update event: " + r.toString)

        if (currentRequest != null) {
            r.slide match {
                case Some(d: Double) => currentRequest.slide = d
                case _ =>
            }
            r.velocity match {
                case Some(d: Double) => currentRequest.velocity = d
                case _ =>
            }
        }

        router ! Broadcast(r)
    }

    def interpreteRequest(json: JsValue) = {
        log.debug(Json.stringify(json))
        val action = (json \ "action").as[String]
        action match {
            case "start" => startSimulation(json)
            case "update" => updateSimulation(json)
            case "stop" => stopSimulation()

            /*
            initialize the current request with info coming from user interface
            this should only be done once
            this is not required when using rest, since start requests will overwrite the current state
             */
            case "init" if currentRequest == null => initState(json)

            case _ =>
        }
    }

    override def receive: Receive = {
        /*
        received from mediator: parse message and execute actions
        */
        case request: JsValue => 
            interpreteRequest(request)

        /*
        update user interface with the current state
        this gets executed when a new web socket is created
        see Application.scala
         */
        case CurrentRequest(webSocket) if currentRequest != null =>
            val message = s"""{
                   |  "action": "current",
                   |  "request": ${Json.stringify(Json.toJson(currentRequest))}
                   |}""".stripMargin
            webSocket ! message
    }
}

object TrafficSimulator {
    def props() = Props(new TrafficSimulator())
    case class CurrentRequest(socket: ActorRef)
}

