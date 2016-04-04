package lib.actions

import com.madgag.playgithub.auth.AuthenticatedSessions.AccessToken
import com.madgag.playgithub.auth.{Client, GHRequest}
import com.madgag.scalagithub.model.{PullRequestId, RepoId}
import controllers.Application._
import controllers.Auth
import lib._
import lib.actions.Requests._
import lib.checks.Check
import org.eclipse.jgit.lib.ObjectId
import play.api.libs.Crypto
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scalax.file.ImplicitConversions._


object Actions {
  private val authScopes = Seq("user:email")

  implicit val authClient: Client = Auth.authClient

  implicit val provider = AccessToken.FromSession

  val GitHubAuthenticatedAction = com.madgag.playgithub.auth.Actions.gitHubAction(authScopes, Bot.workingDir.toPath)

  def githubRepoAction(repoId: RepoId) = GitHubAuthenticatedAction andThen new ActionRefiner[GHRequest, GHRepoRequest] {
    override protected def refine[A](request: GHRequest[A]): Future[Either[Result, GHRepoRequest[A]]] =
      if (Project.byRepoId.contains(repoId)) for (repo <- Bot.conn().getRepo(repoId)) yield {
          Right(new GHRepoRequest(request.gitHubCredentials, repo, request))
      } else Future.successful(Left(Forbidden("Not a supported repo")))
  }

  def githubPRAction(prId: PullRequestId) = githubRepoAction(prId.repo) andThen new ActionRefiner[GHRepoRequest, GHPRRequest] {
    override protected def refine[A](request: GHRepoRequest[A]): Future[Either[Result, GHPRRequest[A]]] = {
      implicit val g = Bot.conn() // get the PR with Bot creds for better caching
      val prF = request.repo.pullRequests.get(prId.num)
      for {
        appUser <- AppUser.from(request)
        pr <- prF
      } yield Right(new GHPRRequest[A](request.gitHubCredentials, appUser, pr, request))
      }.recover {
        case _ => Left(NotFound(s"${request.repo.full_name} doesn't seem to have PR #${prId.num}"))
      }
    }

  val EnsureGitHubVerifiedEmail = new ActionFilter[GHRequest] {
    override protected def filter[A](request: GHRequest[A]): Future[Option[Result]] = for {
      email <- request.userPrimaryEmailF
    } yield {
      if (email.verified) None
      else Some(Forbidden(s"User primary email ${email.email} has not been verified with GitHub"))
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