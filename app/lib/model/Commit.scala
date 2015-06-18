package lib.model

import org.eclipse.jgit.lib.ObjectId

case class Commit(id: ObjectId, message: String) {
  val subject= message.lines.next()
}