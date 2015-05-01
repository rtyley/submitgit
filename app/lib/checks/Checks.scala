package lib.checks

import scala.concurrent.Future

trait Checks[T] {
  class CheckBuilder(p: T => Boolean) {
    final def or(error: String): Check[T] = or(_ => error)

    final def or(error: T => String): Check[T] = new Check[T] {
      override def check(req: T): Future[Option[String]] = Future.successful(if (p(req)) None else Some(error(req)))
    }
  }

  final def check(p: T => Boolean) = new CheckBuilder(p)
}
