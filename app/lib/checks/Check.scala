package lib.checks

import scala.concurrent.{ExecutionContext, Future}

object Check {
  def all[T](t: T, checks: Seq[Check[T]])(implicit ec: ExecutionContext): Future[Seq[String]] =
    Future.traverse(checks)(_.check(t)).map(_.flatten.toList)
}

trait Check[-T] {
  def check(req: T): Future[Option[String]]
}
