package net.kurobako.quickstyle

import cats.effect.IO
import cats.implicits._
import com.google.common.base.Throwables
import com.google.common.io.Resources
import fs2._
import fs2.concurrent.SignallingRef
import net.kurobako.quickstyle.component.FXIOApp
import net.kurobako.quickstyle.component.FXSchedulers._
import org.controlsfx.glyphfont.{FontAwesome, GlyphFont}
import scalafx.Includes._
import scalafx.application.HostServices
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control._
import scalafx.scene.layout.{GridPane, Priority}
import scalafx.scene.{Parent, Scene}
import scalafx.stage.{Stage, Window}


object Application extends FXIOApp {


	Thread.setDefaultUncaughtExceptionHandler { (t, exception) =>
		exception.printStackTrace()
		sys.error(s"Fatal exception in UI thread ${t.getName}")
	}

	case class AppContext[M[_]](mainStage: Stage,
								hostService: HostServices,
								glyphFont: GlyphFont,
								exitSignal: SignallingRef[IO, Boolean]) {

	}


	override def streamFX(args: List[String],
						  ctx: FXIOApp.FXContext,
						  fxStop: SignallingRef[IO, Boolean]): Stream[IO, Unit] = for {




		glyph <- Stream.eval(IO {new FontAwesome(Resources.getResource("fontawesome.otf").openStream())})
		term <- Stream.eval(SignallingRef[IO, Boolean](false))
		ctx <- Stream.emit(AppContext[IO](ctx.mainStage, ctx.hostServices, glyph, term)).covary[IO]

		_ <- Stream.eval(IO {println("Starting")})

		_ <- Stream(
			new FrameController(ctx).effects(root => FXIO {
				Application.configStage(ctx.mainStage)(root, "QuickStyle", 800, 600)
				ctx.mainStage.show()
			})
		).parJoinUnbounded
			.interruptWhen(fxStop)
			.interruptWhen(ctx.exitSignal)
	} yield ()


	def configStage[A <: Parent](stage: Stage)(root: A, title: String,
											   width: Double = -1, height: Double = -1,
											   parent: Option[Window] = None): Stage = {
		stage.title = title
		parent.foreach(stage.initOwner(_))
		stage.scene = new Scene(root, width, height)
		stage
	}


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

	def reportFatal(window: Window,
					e: Throwable,
					signal: SignallingRef[IO, Boolean],
					reason: Option[String] = None): IO[Unit] = FXIO {
		val alert = new Alert(AlertType.Error) {
			initOwner(window)
			title = "Fatal exception"
			headerText = "A fatal exception has occurred"
			contentText = reason.getOrElse(e.getMessage)
		}
		alert.getDialogPane.setExpandableContent(mkExceptionPane(e))
		alert.getDialogPane.setExpanded(true)
		alert.showAndWait()
	} *> signal.set(true)
}
