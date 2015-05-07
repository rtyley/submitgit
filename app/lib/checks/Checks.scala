package lib.checks

import scala.concurrent.{ExecutionContext, Future}

trait Checks[T] {
  class CheckBuilder(p: T => Future[Boolean])(implicit ec: ExecutionContext) {
    final def or(error: String): Check[T] = or(_ => error)

    final def or(error: T => String)(implicit ec: ExecutionContext): Check[T] = new Check[T] {
      override def check(v: T): Future[Option[String]] = p(v).map(if (_) None else Some(error(v)))
    }
  }

  final def check(p: T => Boolean)(implicit ec: ExecutionContext) = new CheckBuilder(p.andThen(Future.successful))

  final def checkAsync(p: T => Future[Boolean])(implicit ec: ExecutionContext) = new CheckBuilder(p)
}
