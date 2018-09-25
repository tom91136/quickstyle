package net.kurobako.quickstyle.component

import java.util.concurrent._

import cats.arrow.FunctionK
import cats.data.OptionT
import cats.effect.{Concurrent, ContextShift, IO, Resource}
import cats.implicits._
import cats.~>
import com.google.common.util.concurrent.ThreadFactoryBuilder
import fs2.Stream
import fs2.concurrent.Queue
import javafx.event.EventHandler
import net.kurobako.bmf.{DirM, FileM}
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty
import scalafx.scene.paint.Color
import scalafx.stage._

import scala.concurrent.ExecutionContext

object FXSchedulers   {

	implicit val IOEC = ExecutionContext.fromExecutorService(new ThreadPoolExecutor(
		0, Integer.MAX_VALUE,
		10L, TimeUnit.SECONDS,
		new SynchronousQueue[Runnable],
		new ThreadFactoryBuilder()
			.setNameFormat("IOEC-%d").setDaemon(true)
			.setUncaughtExceptionHandler { (t, e) =>
				System.err.println(s"Error on thread $t")
				e.printStackTrace(System.err)
			}
			.build()))


	private implicit val FXCS: ContextShift[IO] = IO.contextShift(new ExecutionContext {
		override def execute(runnable: Runnable): Unit = {
			if (Platform.isFxApplicationThread) runnable.run()
			else Platform.runLater(runnable)
		}
		override def reportFailure(t: Throwable): Unit = {
			t.printStackTrace(System.err)
		}
	})

	val IoToStream: FunctionK[IO, Stream[IO, ?]] = λ[IO ~> Stream[IO, ?]](Stream.eval(_))
	


	def FXIO[A](a: => A): IO[A] = IO.shift(FXCS) *> IO(a)


	def joinAndDrain(xs: Stream[IO, Any]*)(implicit ev: Concurrent[IO]): Stream[IO, Unit] = {
		Stream(xs: _*).parJoinUnbounded.drain
	}


	def selectFileDialog(owner: Window, init: Option[DirM[IO]] = None, save: Boolean = false): Stream[IO, Option[FileM[IO]]] = {
		Stream.eval(OptionT(FXIO {

			val picker = new FileChooser
			init.map {_.file.toJava}.foreach {picker.initialDirectory = _}

			Option(if (save) picker.showSaveDialog(owner) else picker.showOpenDialog(owner))
		}).semiflatMap(f => FileM.checked[IO](f)).value)
	}

	def selectDirectoryDialog(owner: Window, init: Option[DirM[IO]] = None): Stream[IO, Option[DirM[IO]]] = {
		Stream.eval(OptionT(FXIO {
			val picker = new DirectoryChooser
			init.map {_.file.toJava}.foreach {picker.initialDirectory = _}
			Option(picker.showDialog(owner))
		}).semiflatMap(f => DirM.checked[IO](f)).value)
	}

	def mkStage(stageStyle: StageStyle = StageStyle.Decorated): Resource[IO, Stage] = {
		Resource.make(FXIO {new Stage(stageStyle)})(s => FXIO {s.close()})
	}

	def stageClosed(stage: Stage)(implicit cs: ContextShift[IO]): Stream[IO, Boolean] =
		eventF(stage.onCloseRequest) >> Stream.emit(true)
	

	private def unsafeRunAsync[A](f: IO[A]): Unit =
		f.start.flatMap(_.join).runAsync(_ => IO.unit).unsafeRunSync()

	def vectorF[A](prop: javafx.collections.ObservableList[A], consInit: Boolean = true)
	: Stream[IO, IndexedSeq[A]] = {
		import scala.collection.JavaConverters._
		for {
			q <- Stream.eval(Queue.unbounded[IO, Vector[A]])
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(prop.asScala.toVector) else IO.unit}
			_ <- Stream.bracket(IO {
				prop.onChange {
					unsafeRunAsync(q.enqueue1(prop.asScala.toVector))
				}
			}) { x => IO {x.cancel()} }
			a <- q.dequeue
		} yield a
	}

	def propF[A](prop: javafx.beans.value.ObservableValue[A], consInit: Boolean = true)
	: Stream[IO, Option[A]] = {
		for {
			q <- Stream.eval(Queue.unbounded[IO, Option[A]])
			_ <- Stream.eval[IO, Unit] {if (consInit) q.enqueue1(Option(prop.value)) else IO.unit}
			_ <- Stream.bracket(IO {
				prop.onChange((_, _, n) => unsafeRunAsync(q.enqueue1(Option(n))))
			}) { x => IO {x.cancel()} }
			a <- q.dequeue
		} yield a
	}.onFinalize(IO {println(s"Kill prop $prop")})


	class EventSink[A](queue: Queue[IO, A]) {


		def first: IO[A] = queue.dequeue1
		def once: Stream[IO, A] = Stream.eval(first)
		def stream: Stream[IO, A] = queue.dequeue

		def registerUnsafe[B <: javafx.event.Event](prop: ObjectProperty[EventHandler[B]])(implicit ev: B =:= A): Unit =
			prop.value = { e => unsafeRunAsync(queue.enqueue1(ev(e))) }

		def registerUnsafe1[B <: javafx.event.Event](prop: ObjectProperty[EventHandler[B]])(a: A): Unit =
			prop.value = { _ => unsafeRunAsync(queue.enqueue1(a)) }
	}
	object EventSink {
		def apply[A]: IO[EventSink[A]] =
			Queue.unbounded[IO, A].map {new EventSink(_)}
	}

	def eventF[A <: javafx.event.Event](prop: ObjectProperty[EventHandler[A]]): Stream[IO, A] = {
		(for {
			q <- Stream.eval(Queue.unbounded[IO, A])
			_ <- Stream.eval[IO, Unit] {
				IO {prop.value = { e => unsafeRunAsync(q.enqueue1(e)) }}
			}
			a <- q.dequeue
		} yield a).onFinalize(IO {println(s"Kill evnet $prop")})
	}


}