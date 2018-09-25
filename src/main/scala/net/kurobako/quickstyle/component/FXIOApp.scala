package net.kurobako.quickstyle.component

import cats.effect._
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import net.kurobako.quickstyle.component.FXIOApp.{FXAppHelper, FXContext}
import scalafx.application.{HostServices, Platform}
import scalafx.stage.Stage



abstract class FXIOApp extends IOApp {

	def streamFX(args: List[String],
				 ctx: FXContext,
				 fxStop: SignallingRef[IO, Boolean]): Stream[IO, Unit]


	override final def run(args: List[String]): IO[ExitCode] = (for {
		fxStop <- Stream.eval(SignallingRef[IO, Boolean](false))
		c <- Stream.eval(IO.async[FXContext] { cb => FXIOApp.ctx = cb }) concurrently
			 Stream.eval(IO.async[Unit] { cb => FXIOApp.stopFn = cb } *> fxStop.set(true)) concurrently
			 Stream.eval(IO.delay {javafx.application.Application.launch(classOf[FXAppHelper], args: _*)})

		_ <- Stream.eval(streamFX(args, c, fxStop).covary[IO].compile.drain)
			.onFinalize(IO.delay {Platform.exit()})
		_ <- Stream.eval(IO.delay(println("Stream ended")))
	} yield ()).compile.drain *> IO.pure(ExitCode.Success)
}

object FXIOApp {
	case class FXContext(mainStage: Stage, hostServices: HostServices)

	private var ctx   : Either[Throwable, FXContext] => Unit = _
	private var stopFn: Either[Throwable, Unit] => Unit      = _

	private class FXAppHelper extends javafx.application.Application {

		import scalafx.Includes._

		override def start(primaryStage: javafx.stage.Stage): Unit = {
			println("FX start")
			FXIOApp.ctx(Right(FXContext(primaryStage, getHostServices)))
		}
		override def stop(): Unit = {
			println("FX stop")
			stopFn(Right(()))
		}
	}

}
