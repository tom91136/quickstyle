package net.kurobako.quickstyle

import cats.data.EitherT
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.google.common.base.Strings
import com.google.common.io.Resources
import com.jakewharton.byteunits.BinaryByteUnit
import fs2._
import fs2.concurrent.SignallingRef
import fs2.io.file._
import javafx.fxml.FXMLLoader
import net.kurobako.bmf.FileM
import net.kurobako.gesturefx.GesturePane
import net.kurobako.quickstyle.Application.AppContext
import net.kurobako.quickstyle.PreviewController.{PreviewConfig, View}
import net.kurobako.quickstyle.component.FXSchedulers._
import org.controlsfx.glyphfont.FontAwesome.Glyph
import org.controlsfx.glyphfont.GlyphFont
import scalafx.Includes._
import scalafx.geometry.Orientation
import scalafx.scene.control._
import scalafx.scene.layout.GridPane._
import scalafx.scene.layout.Priority.{Always, Never}
import scalafx.scene.layout._
import scalafx.scene.{Node, Parent, SceneAntialiasing, SubScene}

import scala.concurrent.duration._

class PreviewController(ctx: AppContext[IO])(implicit cs: ContextShift[IO], timer: Timer[IO]) {


	def effects(attach: Parent => IO[Unit],
				detach: Parent => IO[Unit]): Stream[IO, Unit] = Stream.force(
		for {
			close <- SignallingRef[IO, Boolean](false)
			view <- FXIO(new View(ctx.glyphFont))
			_ <- attach(view.root) *> FXIO {view.cssUrl.requestFocus()}
			sink <- EventSink[PreviewConfig](PreviewConfig())
			_ <- sink.bind(view.cssUrl.text)((pc, url) => pc.copy(cssUrl = url))
			_ <- sink.bind(view.inheritAgent.selected)((pc, inherit) =>
				pc.copy(inheritAgent = inherit.exists(identity)))
		} yield joinAndDrain(
			eventF(view.resetViewport.onAction) >> Stream.eval_(IO(
				view.viewport
					.animate(300 ms)
					.zoomTo(1, view.viewport.viewportCentre()))),
			Stream.eval(loadFxml().flatMap { p => FXIO {view.sandbox.root = p} }),
			eventF(view.close.onAction).flatMap(_ => Stream.eval(detach(view.root) *> close.set(true))),
			sink.discrete.switchMap { config =>
				def update(file: Either[String, FileM[IO]]) =
					EitherT.fromEither[IO](file).semiflatMap(f => for {
						size <- f.size
						changed <- f.lastModified
					} yield
						s"Size: ${BinaryByteUnit.format(size)} Modified: ${changed.toString}")
						.merge.flatMap(x => FXIO {view.status.text = x}) *>
					file.fold(err => FXIO {new Label(s"($err)")},
					{ file =>
						FXIO {view.sandbox.userAgentStylesheet = ""} *>
						FXIO {
							val sheet = file.file.url.toExternalForm
							if (config.inheritAgent) view.sandbox.getRoot.stylesheets = Seq(sheet)
							else view.sandbox.userAgentStylesheet = sheet
						}
					})

				EitherT.fromOption[IO](config.cssUrl.map(_.trim).filterNot(_.isEmpty), "Empty path")
					.flatMap(FileM.checked[IO](_).attemptT.leftMap(_.getMessage))
					.mapK(IoToStream)
					.semiflatMap(f => Stream(f) ++
									  watch[IO](f.nioPath).debounce(100 millisecond).as(f))
					.value
					.flatMap(ef => Stream.eval(update(ef)))
			}
		).interruptWhen(close) ++ Stream.eval_(sink.unbindAll))


	private def loadFxml() =
		FXIO(FXMLLoader.load[javafx.scene.Parent](Resources.getResource("ui-mosaic.fxml")))


}
object PreviewController {

	private case class PreviewConfig(cssUrl: Option[String] = None,
									 inheritAgent: Boolean = true,
									 fxmlUrl: String = "")

	private class View(glyph: GlyphFont) {

		import scalafx.scene.{Node => SNode}


		val sandbox: SubScene = new SubScene(
			width = 0, height = 0,
			depthBuffer = true,
			antiAliasing = SceneAntialiasing.Balanced)

		val viewport = new GesturePane(sandbox)

		val cssUrl       : TextField    = new TextField
		val inheritAgent : CheckBox     = new CheckBox("Inherit default") {selected = true}
		val fxmlUrl      : TextField    = new TextField
		val status       : Label        = new Label
		val frame        : StackPane    = new StackPane {
			vgrow = Priority.Always
			children = viewport
		}
		val close        : Button       = new Button("", glyph.create(Glyph.CLOSE))
		val resetViewport: Button       = new Button("", glyph.create(Glyph.UNDO))
		val lockViewport : ToggleButton = new ToggleButton("", glyph.create(Glyph.LOCK))

		sandbox.width <== frame.width
		sandbox.height <== frame.height
		viewport.gestureEnabledProperty <== !lockViewport.selected

		val root: VBox = new VBox(
			new GridPane {
				hgap = 4
				vgap = 4
				styleClass += "tool-bar"
				columnConstraints = Seq(
					new ColumnConstraints {hgrow = Never},
					new ColumnConstraints {hgrow = Always},
					new ColumnConstraints {hgrow = Never},
					new ColumnConstraints {hgrow = Never},
				)
				rowConstraints = Seq(
					new RowConstraints {vgrow = Never},
					new RowConstraints {vgrow = Never},
				)
				private def g(col: Int, row: Int)(child: Node): Unit =
					setConstraints(child, col, row)
				def @:[A <: SNode](f: SNode => Unit, node: A): A = {f(node); node}

				children = Seq(
					// row 0
					@:(g(0, 0), new Label("CSS")),
					@:(g(1, 0), cssUrl),
					@:(g(2, 0), inheritAgent),
					@:(g(3, 0), close),

					// row 1
					// TODO
//					@:(g(0, 1), new Label("FXML")), @:(g(1, 1), fxmlUrl),
				)
			},
			frame,
			new HBox {
				children = Seq(
					status,
					new Separator {orientation = Orientation.Vertical},
					new Label {
						text <== viewport.currentScaleProperty()
							.multiply(100)
							.asString("Scale: %.2f%%")
					}, lockViewport, resetViewport)
				styleClass += "tool-bar"
			}
		)

	}

}
