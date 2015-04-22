package controllers

import com.github.nscala_time.time.Imports._
import controllers.Application._
import lib.github.Implicits._
import org.kohsuke.github.{GHPullRequest, GHRepository, GitHub}
import play.api.mvc.Security.AuthenticatedBuilder
import play.api.mvc._

import lib.github.Implicits._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

class GHRequest[A](val gitHub: GitHub, request: Request[A]) extends WrappedRequest[A](request)

class GHRepoRequest[A](gitHub: GitHub, val repo: GHRepository, request: Request[A]) extends GHRequest[A](gitHub, request)

class GHPRRequest[A](gitHub: GitHub, val pr: GHPullRequest, request: Request[A]) extends GHRepoRequest[A](gitHub, pr.getRepository, request)

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


  def githubRepoAction(repoOwner: String, repoName: String) = githubAction() andThen new ActionRefiner[GHRequest, GHRepoRequest] {
    override protected def refine[A](request: GHRequest[A]): Future[Either[Result, GHRepoRequest[A]]] = Future {
      val gitHub = request.gitHub
      val repo = gitHub.getRepository(s"$repoOwner/$repoName")
      Either.cond(repoWhiteList(repo.getFullName), new GHRepoRequest(gitHub, repo, request), Forbidden("Not a supported repo"))
    }
  }

  def githubPRAction(repoOwner: String, repoName: String, num: Int) = githubRepoAction(repoOwner, repoName) andThen new ActionRefiner[GHRepoRequest, GHPRRequest] {
    override protected def refine[A](request: GHRepoRequest[A]): Future[Either[Result, GHPRRequest[A]]] = Future {
      Try(request.repo.getPullRequest(num)) match {
        case Success(pr) => Right(new GHPRRequest[A](request.gitHub, pr, request))
        case Failure(e) => Left(NotFound(s"${request.repo.getFullName} doesn't seem to have PR #$num"))
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

  val EnsureSeemsLegit = new ActionFilter[GHRequest] {
    override protected def filter[A](request: GHRequest[A]): Future[Option[Result]] = Future {
      val user = request.gitHub.getMyself
      if (user.createdAt > DateTime.now - 3.months) Some(Forbidden(s"${user.atLogin}'s GitHub account is less than 3 months old"))
      else if (user.verifiedEmails.isEmpty) Some(Forbidden(s"No verified emails on ${user.atLogin}'s GitHub account"))
      else None
    }
  }

  class LegitFilter[G[X] <: GHRequest[X]] extends ActionFilter[G] {
    override protected def filter[A](request: G[A]): Future[Option[Result]] = Future {
      val user = request.gitHub.getMyself
      if (user.createdAt > DateTime.now - 3.months) Some(Forbidden(s"${user.atLogin}'s GitHub account is less than 3 months old"))
      else if (user.verifiedEmails.isEmpty) Some(Forbidden(s"No verified emails on ${user.atLogin}'s GitHub account"))
      else None
    }
  }

}