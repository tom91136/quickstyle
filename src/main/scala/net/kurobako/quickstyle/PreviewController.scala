package net.kurobako.quickstyle

import cats.data.EitherT
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.google.common.io.Resources
import com.jakewharton.byteunits.BinaryByteUnit
import com.sun.javafx.css.StyleManager
import fs2._
import fs2.io.file._
import javafx.fxml.FXMLLoader
import net.kurobako.bmf.FileM
import net.kurobako.quickstyle.PreviewController.View
import net.kurobako.quickstyle.component.FXSchedulers._
import scalafx.Includes._
import scalafx.scene.control.{Button, Label, ScrollPane, TextField}
import scalafx.scene.layout.{Priority, StackPane, VBox}
import scalafx.scene.{Node, Parent, SubScene}

import scala.concurrent.duration._

class PreviewController {


	def effects(attach: Parent => IO[Unit])(implicit cs: ContextShift[IO], timer: Timer[IO]): Stream[IO, Unit] = {

		for {
			view <- Stream.eval(FXIO {new View})
			_ <- Stream.eval(attach(view.root))
			_ <- propF(view.url.text, consInit = true)
				.through(streams.switchMap { url =>
					EitherT.fromOption[IO](url.filterNot(_.isBlank), "Empty path")
						.flatMap(FileM.checked[IO](_).attemptT.leftMap(_.getMessage))
						.mapK(IoToStream)
						.semiflatMap(f => Stream(f) ++
										  watch[IO](f.nioPath).debounce(100 millisecond).as(f))
						.value
						.flatMap(ef => Stream.eval(update(view, ef)))
				})

		} yield ()
	}

	def update(view: View, file: Either[String, FileM[IO]]) =
		EitherT.fromEither[IO](file).semiflatMap(f => for {
			size <- f.size
			changed <- f.lastModified
		} yield
			s"""Size     : ${BinaryByteUnit.format(size)}
			   |Modified : ${changed.toString}""".stripMargin)
			.merge.flatMap(x => FXIO {view.status.text = x}) *>
		file.fold(err => FXIO {new Label(s"($err)")}, mkPreview(view, _))
			.flatMap { x => FXIO {view.frame.children = x} }


	def mkPreview(view: View, file: FileM[IO]): IO[Node] = {
		FXIO {
			println(s"Using stylesheet: ${file.file.url.toExternalForm}")
			StyleManager.errorsProperty().onChange( println(StyleManager.getErrors))
			view.scene.userAgentStylesheet = file.file.url.toExternalForm
			view.scene
		}
	}


}
object PreviewController {


	class View {


	
		val url   : TextField = new TextField
		val status: Label     = new Label
		val frame : StackPane = new StackPane
		
		val scene: SubScene = new SubScene(0, 0){
			root = FXMLLoader.load(Resources.getResource("ui-mosaic.fxml")) : javafx.scene.Parent
			width <== frame.width
			height <== frame.height
		}

		val root  : VBox      = new VBox(
			new VBox(url, status) {styleClass += "tool-bar"},
			new ScrollPane{
				content = frame
				fitToHeight = true
				fitToWidth = true
				vgrow = Priority.Always
			}
		)

	}

}
