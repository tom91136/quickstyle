package net.kurobako.quickstyle.component

import javafx.application.Platform
import javafx.scene.{Node, Parent}
import javafx.util.Duration
import scalafx.Includes._
import scalafx.animation.{FadeTransition, Interpolator}
import scalafx.beans.property.ObjectProperty
import scalafx.geometry.Insets
import scalafx.scene.layout.{Background, BackgroundFill, CornerRadii}
import scalafx.scene.paint.Paint
import scalafx.scene.{Node => SNode}

import scala.concurrent.ExecutionContext


object RichScalaFX {


	implicit val JavaFXEC = ExecutionContext.fromExecutor(Platform.runLater(_), {(_: Throwable).printStackTrace()})
	

	def @:[A <: SNode](f: SNode => Unit, node: A): A = {f(node); node}


	implicit class RichPaint(paint: Paint) {

		def asBackground(radii: CornerRadii = null, insets: Insets = null): Background =
			new Background(Array(new BackgroundFill(paint, radii, insets)))

	}

	implicit class RichObjectProperty[T](prop: ObjectProperty[T]) {

		def modify(f: T => T): Unit = prop.update(f(prop.value))

	}
	

	def findOrMkUnsafe[T <: Node, P <: Parent](p: P, id: String)
											  (an: (P, T) => Unit)(mk: => T): T =
		Option(p.lookup(s"#$id")) match {
			case Some(v) => v.asInstanceOf[T]
			case None    =>
				val n = mk
				n.setId(id)
				an(p, n)
				n
		}


	implicit class fadeInTransition[+N <: SNode](value: N) {
		def fadeIn(from: Double, to: Double, duration: Duration): Unit = {
			new FadeTransition(duration, value) {
				interpolator = Interpolator.EaseBoth
				fromValue = from
				toValue = to
			}.play()
		}
	}

}
