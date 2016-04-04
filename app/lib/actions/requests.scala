package lib.actions

import com.madgag.okhttpscala._
import com.madgag.playgithub.auth.GHRequest
import com.madgag.scalagithub.GitHub._
import com.madgag.scalagithub.GitHubCredentials
import com.madgag.scalagithub.model.{PullRequest, Repo}
import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import lib.MailType.{Live, Preview}
import lib._
import lib.model.PatchCommit
import play.api.mvc.Request

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Requests {

  class GHRepoRequest[A](gitHubCredentials: GitHubCredentials, val repo: Repo, request: Request[A]) extends GHRequest[A](gitHubCredentials, request)

  class GHPRRequest[A](gitHubCredentials: GitHubCredentials, val user: AppUser, val pr: PullRequest, request: Request[A]) extends GHRepoRequest[A](gitHubCredentials, pr.baseRepo, request) {
    implicit val g = gitHub

    lazy val userOwnsPR = user.id == pr.user.id

    lazy val patchCommitsF: Future[Seq[PatchCommit]] = {
      for {
        ghCommits <- pr.commits.list().all()
        patch <- new OkHttpClient().execute(new okhttp.Request.Builder().url(pr.patch_url).build())(_.body().string)
      } yield {
        PatchCommit.from(ghCommits, patch)
      }
    }

    lazy val hasBeenPreviewed = PreviewSignatures.hasPreviewed(pr)(request)

    lazy val defaultMailType = if (hasBeenPreviewed) Live else Preview
  }

}