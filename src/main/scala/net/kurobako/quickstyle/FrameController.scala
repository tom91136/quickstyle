package net.kurobako.quickstyle

import cats.effect.{ContextShift, IO, Timer}
import fs2.Stream
import net.kurobako.quickstyle.Application.AppContext
import net.kurobako.quickstyle.FrameController.View
import net.kurobako.quickstyle.component.FXSchedulers._
import scalafx.Includes._
import scalafx.scene.Parent
import scalafx.scene.control.{Menu, MenuBar, MenuItem, SplitPane}
import scalafx.scene.layout.{Priority, VBox}

class FrameController(ctx: AppContext[IO]) {

	def effects(attach: Parent => IO[Unit])(implicit cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, Unit] = {
		for {
			view <- Stream.eval(FXIO {new View})
			_ <- Stream.eval(attach(view.root))
			_ <- joinAndDrain(
				eventF(view.exit.onAction).evalMap(_ => ctx.exitSignal.set(true)),
				eventF(view.addSbs.onAction)
					.mapAsync(Int.MaxValue) { _ =>
						new PreviewController().effects(n => FXIO {view.frames.items += n}).compile.drain
					}
			)
		} yield ()
	}


}
object FrameController {


	class View {


		val exit  : MenuItem = new MenuItem("Exit")
		val addSbs: MenuItem = new MenuItem("Add pane(side by side")


		val menu  : MenuBar   = new MenuBar {
			menus = Seq(
				new Menu("File") {
					items = Seq(exit)
				},
				new Menu("View") {
					items = Seq(addSbs)
				})
		}
		val frames: SplitPane = new SplitPane {
			vgrow = Priority.Always
		}
		val root  : VBox      = new VBox(menu, frames)

	}
}

