package net.kurobako.quickstyle

import cats.arrow.FunctionK
import cats.effect.{Concurrent, IO}
import cats.effect.concurrent.{Deferred, Ref, Semaphore}
import cats.implicits._
import cats.~>
import fs2.concurrent.SignallingRef
import fs2.{Pipe, Stream}

object streams {


	def switchingHandler[F[_], A](f: A => Seq[Stream[F, Any]])(implicit F: Concurrent[F]): Pipe[F, A, Nothing] = {
		self => self.through(switchMap(a => Stream.emits(f(a)).parJoinUnbounded.drain))
	}



	def switchMap[F[_], F2[x] >: F[x], O, O2](f: O => Stream[F2, O2])(implicit F2: Concurrent[F2]): Pipe[F2, O, O2] = {
		self =>
			Stream.force(Semaphore[F2](1).flatMap {
				guard =>
					Ref.of[F2, Option[Deferred[F2, Unit]]](None).map { haltRef =>
						def runInner(o: O, halt: Deferred[F2, Unit]): Stream[F2, O2] =
							Stream.eval(guard.acquire) >> // guard inner to prevent parallel inner streams
							f(o).interruptWhen(halt.get.attempt) ++ Stream.eval_(guard.release)

						self
							.evalMap { o =>
								Deferred[F2, Unit].flatMap { halt =>
									haltRef
										.getAndSet(halt.some)
										.flatMap {
											case None       => F2.unit
											case Some(last) => last.complete(()) // interrupt the previous one
										}
										.as(runInner(o, halt))
								}
							}
							.parJoin(2)
					}
			})
	}

}
