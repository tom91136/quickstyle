package net.kurobako.quickstyle

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2.Stream
import net.kurobako.jfx.Event._
import net.kurobako.jfx.FXApp.FXContextShift
import net.kurobako.jfx._
import net.kurobako.quickstyle.Application.AppContext
import net.kurobako.quickstyle.FrameController.View
import scalafx.Includes._
import scalafx.scene.Scene
import scalafx.scene.control.{Menu, MenuBar, MenuItem, SplitPane}
import scalafx.scene.layout.{Priority, VBox}
import scalafx.stage.Stage

class FrameController private(stage: Stage, ctx: AppContext[IO])
							 (implicit cs: ContextShift[IO], timer: Timer[IO]) {

	import ctx.fx._


	def effects(): Stream[IO, Unit] = {
		for {
			view <- Stream.eval(FXIO {
				val view = new View
				stage.title = "QuickStyle"
				stage.scene = new Scene(view.root, 800, 600)
				stage.show()
				view
			})
			_ <- joinAndDrain(
				Stream.eval(addPreview(view)),
				event(view.exit.onAction).evalMap(_ => ctx.exitSignal.set(true)),
				event(view.newWindow.onAction).evalMap(_ => FrameController.make(ctx, new Stage)),
				event(view.addSbs.onAction).mapAsync(Int.MaxValue)(_ => addPreview(view)),
			).interruptWhen(event(stage.onCloseRequest).as(true)) ++ Stream.eval(IO {println("Died")})
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


	def make(ctx: AppContext[IO], stage: Stage)
			(implicit cs: ContextShift[IO], timer: Timer[IO], fxcs: FXContextShift): IO[Unit] =
		ctx.effects.enqueue1(new FrameController(stage, ctx).effects())

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

