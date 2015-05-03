package controllers

import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request.Builder
import controllers.Application._
import lib.checks.Check
import lib.github.Implicits._
import lib.github.{PullRequestId, RepoName}
import lib.okhttpscala._
import lib.{MailType, Patch, Patches, PreviewSignatures}
import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github.{GHPullRequest, GHRepository, GitHub}
import play.api.libs.Crypto
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._

import scala.collection.convert.wrapAll._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


class GHRequest[A](val gitHub: GitHub, request: Request[A]) extends WrappedRequest[A](request) {
  val user = gitHub.getMyself

  lazy val userEmail = user.primaryEmail
}

class GHRepoRequest[A](gitHub: GitHub, val repo: GHRepository, request: Request[A]) extends GHRequest[A](gitHub, request)

class GHPRRequest[A](gitHub: GitHub, val pr: GHPullRequest, request: Request[A]) extends GHRepoRequest[A](gitHub, pr.getRepository, request) {
  lazy val userOwnsPR = user == pr.getUser

  lazy val commitsAndPatchesF: Future[Seq[(ObjectId, Patch)]] = {
    val commits = pr.listCommits().toSeq
    require(commits.size < 10)

    val patchUrl = pr.getPatchUrl.toString
    for {
      resp <- new OkHttpClient().execute(new Builder().url(patchUrl).build())
    } yield {
      val patch = resp.body.string
      Patches.commitsAndPatches(commits.map(c => ObjectId.fromString(c.getSha)), patch)
    }
  }

}

object Actions {
  val redirectToGitHubForAuth = Redirect(ghAuthUrl)

  def userGitHubConnectionForSessionAccessToken(req: RequestHeader): Option[GitHub] = for {
    accessToken <- req.session.get(AccessTokenSessionKey)
    gitHubConn <- Try(GitHub.connectUsingOAuth(accessToken)).toOption
  } yield gitHubConn

  val GitHubAuthenticatedAction = new AuthenticatedBuilder(userGitHubConnectionForSessionAccessToken, _ => redirectToGitHubForAuth)

  // onUnauthorized: RequestHeader => Result = _ => Unauthorized()
  def githubAction() = GitHubAuthenticatedAction andThen new ActionTransformer[AuthRequest, GHRequest] {
    override protected def transform[A](request: AuthRequest[A]): Future[GHRequest[A]] = Future.successful {
      new GHRequest[A](request.user, request)
    }
  }


  def githubRepoAction(repoId: RepoName) = githubAction() andThen new ActionRefiner[GHRequest, GHRepoRequest] {
    override protected def refine[A](request: GHRequest[A]): Future[Either[Result, GHRepoRequest[A]]] = Future {
      Either.cond(repoWhiteList(repoId.fullName), {
        val gitHub = request.gitHub
        val repo = gitHub.getRepository(repoId.fullName)
        new GHRepoRequest(gitHub, repo, request)}, Forbidden("Not a supported repo"))
    }
  }

  def githubPRAction(prId: PullRequestId) = githubRepoAction(prId.repoName) andThen new ActionRefiner[GHRepoRequest, GHPRRequest] {
    override protected def refine[A](request: GHRepoRequest[A]): Future[Either[Result, GHPRRequest[A]]] = Future {
      Try(request.repo.getPullRequest(prId.num)) match {
        case Success(pr) => Right(new GHPRRequest[A](request.gitHub, pr, request))
        case Failure(e) => Left(NotFound(s"${request.repo.getFullName} doesn't seem to have PR #${prId.num}"))
      }
    }
  }

  def ensureGitHubVerified(email: String) = new ActionFilter[GHRequest] {
    override protected def filter[A](request: GHRequest[A]): Future[Option[Result]] = Future {
      val user = request.gitHub.getMyself
      if (user.verifiedEmails.map(_.getEmail).contains(email)) None
      else Some(Forbidden(s"Not a GitHub-verified email for ${user.atLogin}"))
    }
  }

  def verifyCommitSignature[G[X] <: Request[X]](headCommit: ObjectId, signature: Option[String] = None) = new ActionFilter[G] {
    override protected def filter[A](request: G[A]): Future[Option[Result]] = Future {
      val sig = signature.getOrElse(request.session(PreviewSignatures.keyFor(headCommit)))
      if (Crypto.constantTimeEquals(sig, PreviewSignatures.signatureFor(headCommit))) None else
        Some(Unauthorized("No valid Preview signature"))
    }
  }

  def mailChecks(mailType: MailType) = new ActionFilter[GHPRRequest] {
    override protected def filter[A](req: GHPRRequest[A]): Future[Option[Result]] = for {
      errors <- Check.all(req, mailType.checks)
    } yield if (errors.isEmpty) None else Some(Forbidden(errors.mkString("\n")))
  }

}