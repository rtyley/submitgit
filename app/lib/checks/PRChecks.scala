package lib.checks

import com.github.nscala_time.time.Imports._
import com.madgag.github.Implicits._
import com.madgag.playgithub.auth.GHRequest
import lib.Dates._
import lib.actions.Requests._
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import org.joda.time.Period
import org.kohsuke.github.GHIssueState

import scala.concurrent.ExecutionContext.Implicits.global

object GHChecks extends Checks[GHRequest[_]] {

  val EmailVerified = check(_.userEmail.isVerified) or
    (req => s"Verify your email address (${req.userEmail.getEmail}) with GitHub")

  val UserHasNameSetInProfile = check(_.user.name.exists(_.length > 1)) or
    "Set a Name in your GitHub profile - it'll be used as a display name next to your email"

  def accountIsOlderThan(period: Period) = check(_.user.createdAt < DateTime.now - period) or
    s"To prevent spam, we don't currently allow GitHub accounts less than ${period.pretty} old - get in touch if that's a problem for you!"

  val RegisteredEmailWithSES = checkAsync(req => ses.getIdentityVerificationStatusFor(req.userEmail.getEmail).map(_.contains(VerificationStatus.Success))) or
    (req => s"Register your email address (${req.userEmail.getEmail}) with submitGit's Amazon SES account in order for it to send emails from you.")

}

object PRChecks extends Checks[GHPRRequest[_]] {

  val MaxCommits = 30

  val UserOwnsPR = check(_.userOwnsPR) or (req => s"This PR was raised by ${req.pr.getUser.atLogin} - you can't submit it for them") // fatal

  val PRIsOpen = check(_.pr.getState == GHIssueState.OPEN) or "Can't submit a closed pull request - reopen it if you're sure"

  val HasBeenPreviewed = check(_.hasBeenPreviewed) or (req => s"You need to preview these commits in a test email to yourself before they can be sent for real - click the link at the bottom of the preview email!")

  val HasAReasonableNumberOfCommits = checkAsync(_.patchCommitsF.map(_.length <= MaxCommits)) or (req => s"For now, submitGit won't submit PRs with more than $MaxCommits commits")

  val CommitSubjectsAreNotTooLong = checkAsync(_.patchCommitsF.map(_.exists(_.commit.subject.size > 72))) or (req => s"Your commit subject lines are too long...")

}
