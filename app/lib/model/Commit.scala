package lib.model

import org.eclipse.jgit.lib.ObjectId

case class Commit(
  id: ObjectId,
  author: UserIdent,
  committer: UserIdent,
  message: String
) {
  val subject = message.lines.next()
}