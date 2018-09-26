package net.kurobako.quickstyle

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2.Stream
import net.kurobako.quickstyle.Application.AppContext
import net.kurobako.quickstyle.FrameController.View
import net.kurobako.quickstyle.component.FXSchedulers._
import scalafx.Includes._
import scalafx.scene.control.{Menu, MenuBar, MenuItem, SplitPane}
import scalafx.scene.layout.{Priority, VBox}
import scalafx.scene.{Parent, Scene}
import scalafx.stage.Stage

class FrameController private(ctx: AppContext[IO])(implicit cs: ContextShift[IO], timer: Timer[IO]) {

	def effects(attach: Parent => IO[Unit]): Stream[IO, Unit] = {
		for {
			view <- Stream.eval(FXIO {new View})
			_ <- Stream.eval(attach(view.root))
			_ <- joinAndDrain(
				Stream.eval(addPreview(view)),
				eventF(view.exit.onAction).evalMap(_ => ctx.exitSignal.set(true)),
				eventF(view.newWindow.onAction).evalMap(_ => FrameController.make(ctx)),
				eventF(view.addSbs.onAction).mapAsync(Int.MaxValue)(_ => addPreview(view))
			)
			_ <- Stream.eval(IO {println("Died")})
		} yield ()
	}

	private def addPreview(view: View): IO[Unit] =
		new PreviewController(ctx).effects(
			n => FXIO {
				view.frames.items += n
				val length = view.frames.items.length
				val width = 1.0 / length
				view.frames.dividerPositions = (1 to length).map(i => i * width): _*
			}, n => FXIO {view.frames.items -= n}).compile.drain

}
object FrameController {


	def make(ctx: AppContext[IO], useMainStage: Boolean = false)
			 (implicit cs: ContextShift[IO],
			  timer: Timer[IO]): IO[Unit] = ctx.effects.enqueue1(
		new FrameController(ctx).effects(p => FXIO {
			val stage = if (useMainStage) ctx.fx.mainStage else new Stage
			stage.title = "QuickStyle"
			stage.scene = new Scene(p, 800, 600)
			stage.show()
		})
	)

	private class View {


		val exit     : MenuItem = new MenuItem("Exit")
		val newWindow: MenuItem = new MenuItem("New window")
		val addSbs   : MenuItem = new MenuItem("Add pane(side by side")

		val menu  : MenuBar   = new MenuBar {
			useSystemMenuBar = true
			menus = Seq(
				new Menu("File") {items = Seq(newWindow, exit)},
				new Menu("View") {items = Seq(addSbs)})
		}
		val frames: SplitPane = new SplitPane {vgrow = Priority.Always}
		val root  : VBox      = new VBox(menu, frames)

	}
}

