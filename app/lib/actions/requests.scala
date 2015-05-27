package lib.actions

import com.madgag.github.Implicits._
import com.madgag.okhttpscala._
import com.madgag.playgithub.auth.GHRequest
import com.squareup.okhttp
import com.squareup.okhttp.OkHttpClient
import lib.{MailType, Patch, Patches, PreviewSignatures}
import org.kohsuke.github.{GHPullRequest, GHPullRequestCommitDetail, GHRepository, GitHub}
import play.api.mvc.Request

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Requests {
  implicit class RichGHRequest[A](req: GHRequest[A]) {
    lazy val userEmail = req.user.primaryEmail
  }

  class GHRepoRequest[A](gitHub: GitHub, val repo: GHRepository, request: Request[A]) extends GHRequest[A](gitHub, request)

  class GHPRRequest[A](gitHub: GitHub, val pr: GHPullRequest, request: Request[A]) extends GHRepoRequest[A](gitHub, pr.getRepository, request) {
    lazy val userOwnsPR = user == pr.getUser

    lazy val commitsAndPatchesF: Future[Seq[(GHPullRequestCommitDetail, Patch)]] = {
      val commits = pr.listCommits().toSeq
      //require(commits.size < 50)

      val patchUrl = pr.getPatchUrl.toString
      for {
        resp <- new OkHttpClient().execute(new okhttp.Request.Builder().url(patchUrl).build())
      } yield {
        val patch = resp.body.string
        val patchesByObjectId = Patches.commitsAndPatches(commits.map(_.objectId), patch).toMap
        commits.map(c => (c, patchesByObjectId(c.objectId)))
      }
    }

    lazy val hasBeenPreviewed = PreviewSignatures.hasPreviewed(pr)(request)

    lazy val defaultMailType = if (hasBeenPreviewed) MailType.Live else MailType.Preview
  }

}