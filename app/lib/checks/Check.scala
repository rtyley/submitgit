package lib.checks

import scala.concurrent.Future

trait Check[T] {
  def check(req: T): Future[Option[String]]
}
