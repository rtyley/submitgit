package controllers

import lib.MailType
import lib.github.{PullRequestId, RepoName}
import org.eclipse.jgit.lib.ObjectId
import play.api.mvc.PathBindable.Parsing

object Binders {

  implicit object bindableObjectId extends Parsing[ObjectId](
    ObjectId.fromString, _.name, (key: String, e: Exception) => s"Cannot parse parameter '$key' as a commit id: ${e.getMessage}"
  )

  implicit object bindableMailType extends Parsing[MailType](
    MailType.bySlug, _.slug, (key: String, e: Exception) => s"Cannot parse parameter '$key' as a commit id: ${e.getMessage}"
  )

  implicit object bindableRepoName extends Parsing[RepoName](
    RepoName.from, _.fullName, (key: String, e: Exception) => s"Cannot parse repo name '$key'"
  )

  implicit object bindablePullRequestId extends Parsing[PullRequestId](
    PullRequestId.from, _.slug, (key: String, e: Exception) => s"Cannot parse pull request '$key'"
  )
}
