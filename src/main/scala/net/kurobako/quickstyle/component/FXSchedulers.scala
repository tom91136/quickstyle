package net.kurobako.quickstyle.component

import java.util.concurrent._

import cats.arrow.FunctionK
import cats.effect.{Concurrent, ContextShift, IO}
import cats.implicits._
import cats.~>
import com.google.common.util.concurrent.ThreadFactoryBuilder
import fs2.Stream
import fs2.concurrent.{Queue, SignallingRef}
import javafx.event.EventHandler
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.beans.property.ObjectProperty

import scala.concurrent.ExecutionContext

object FXSchedulers {

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


	def FXIO[A](a: => A)(implicit cs: ContextShift[IO]): IO[A] = IO.shift(FXCS) *> IO(a) <* IO.shift(cs)


	def joinAndDrain(xs: Stream[IO, Any]*)(implicit ev: Concurrent[IO]): Stream[IO, Unit] = {
		Stream(xs: _*).parJoinUnbounded.drain
	}

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


	class EventSink[A] private(private val as: SignallingRef[IO, A],
							   private val refs: SignallingRef[IO, List[IO[Unit]]]) {

		val discrete: Stream[IO, A] = as.discrete

		def unbindAll: IO[Unit] = refs.getAndSet(Nil).flatMap(_.sequence).void

		def bind[B](prop: javafx.beans.value.ObservableValue[B])(f: (A, Option[B]) => A): IO[Unit] =
			IO {prop.onChange((_, _, n) => unsafeRunAsync(as.update(f(_, Option(n)))))}
				.flatMap(s => refs.update(IO(s.cancel()) :: _))

		def bind[B <: javafx.event.Event](prop: ObjectProperty[EventHandler[B]])(f: (A, B) => A): IO[Unit] =
			IO {prop.value = { e => unsafeRunAsync(as.update(f(_, e))) }} *>
			refs.update(IO(prop.value = null) :: _)

	}

	object EventSink {
		def apply[A](a: A): IO[EventSink[A]] = for {
			as <- SignallingRef[IO, A](a)
			refs <- SignallingRef[IO, List[IO[Unit]]](Nil)
		} yield new EventSink(as, refs)
	}

	def eventF[A <: javafx.event.Event](prop: ObjectProperty[EventHandler[A]]): Stream[IO, A] = {
		for {
			q <- Stream.eval(Queue.unbounded[IO, A])
			_ <- Stream.eval[IO, Unit] {
				IO {prop.value = { e => unsafeRunAsync(q.enqueue1(e)) }}
			}
			a <- q.dequeue
		} yield a
	}


}