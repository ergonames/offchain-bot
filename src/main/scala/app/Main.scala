package app

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import execute.akkaFunctions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class ErgoNamesJob() extends Actor with ActorLogging {
  override def receive: Receive = { case _ =>
    mint()
  }

  def mint(): Unit = {
    val akka = new akkaFunctions

    akka.main()
  }
}

object Main extends App {

  private val schedulerActorSystem = ActorSystem("ErgoNamesBot")

  private val jobs: ActorRef = schedulerActorSystem.actorOf(
    Props(
      new ErgoNamesJob()
    ),
    "scheduler"
  )

  schedulerActorSystem.scheduler.scheduleAtFixedRate(
    initialDelay = 2.seconds,
    interval = 60.seconds,
    receiver = jobs,
    message = ""
  )

  // Keep the main thread alive until the actor system is manually terminated.
  Await.result(schedulerActorSystem.whenTerminated, Duration.Inf)
}
