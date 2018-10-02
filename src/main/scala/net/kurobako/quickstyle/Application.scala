package net.kurobako.quickstyle

import cats.effect.IO
import cats.implicits._
import com.google.common.base.Throwables
import com.google.common.io.Resources
import fs2._
import fs2.concurrent.{Enqueue, Queue, SignallingRef}
import net.kurobako.jfx.FXApp.FXContext
import net.kurobako.jfx.{FXApp, _}
import org.controlsfx.glyphfont.{FontAwesome, GlyphFont}
import scalafx.Includes._
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.layout.{GridPane, Priority}
import scalafx.stage.Window


object Application extends FXApp {


	case class AppContext[M[_]](fx: FXContext,
								glyphs: GlyphFont,
								exitSignal: SignallingRef[IO, Boolean],
								effects: Enqueue[M, Stream[M, Unit]])

	override def streamFX(args: List[String],
						  fx: FXApp.FXContext, stage: javafx.stage.Stage): Stream[IO, Unit] = Stream.force(
		for {
			glyph <- IO(new FontAwesome(Resources.getResource("fontawesome.otf").openStream()))
			term <- SignallingRef[IO, Boolean](false)
			stages <- Queue.synchronous[IO, Stream[IO, Unit]]
			ctx <- IO.pure(AppContext[IO](
				fx = fx,
				glyphs = glyph,
				exitSignal = term,
				effects = stages))
		} yield Stream.eval_(IO {println("Starting")}) ++
				(stages.dequeue.parJoinUnbounded
					 .interruptWhen(fx.halt)
					 .interruptWhen(term)
					 .onError { case e =>
						 Stream.eval(reportFatal(
							 exception = e,
							 signal = term,
							 reason = "Application crashed".some))
					 } concurrently Stream.eval_(FrameController.make(ctx, stage))) ++
				Stream.eval_(IO {println("Stopping")})
	)


	def mkExceptionPane(e: Throwable): GridPane = new GridPane {
		maxWidth = Double.MaxValue
		add(new Label("The exception stacktrace was:"), 0, 0)
		add(new TextArea(Throwables.getStackTraceAsString(e)) {
			editable = false
			wrapText = false
			maxWidth = Double.MaxValue
			maxHeight = Double.MaxValue
			hgrow = Priority.Always
			vgrow = Priority.Always
		}, 0, 1)
	}

	def reportNonFatal(window: Window,
					   header: String,
					   e: Throwable,
					   reason: Option[String] = None): IO[Unit] = FXIO {
		val alert = new Alert(AlertType.Error) {
			initOwner(window)
			title = "Error"
			headerText = header
			contentText = reason.getOrElse(e.getMessage)
		}
		alert.getDialogPane.setExpandableContent(mkExceptionPane(e))
		alert.getDialogPane.setExpanded(true)
		alert.showAndWait()
	}

	def reportFatal(exception: Throwable,
					signal: SignallingRef[IO, Boolean],
					window: Option[Window] = None,
					reason: Option[String] = None): IO[Unit] = FXIO {
		val alert = new Alert(AlertType.Error) {
			window.foreach(initOwner(_))
			title = "Fatal exception"
			headerText = "A fatal exception has occurred"
			contentText = reason.getOrElse(exception.getMessage)
		}
		alert.getDialogPane.setExpandableContent(mkExceptionPane(exception))
		alert.getDialogPane.setExpanded(true)
		alert.showAndWait()
	} *> signal.set(true)
}
