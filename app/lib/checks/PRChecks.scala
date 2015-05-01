package lib.checks

import controllers.GHPRRequest
import lib.PreviewSignatures
import lib.aws.SES._
import lib.aws.SesAsyncHelpers._
import lib.github.Implicits._
import org.kohsuke.github.GHIssueState

import scala.concurrent.ExecutionContext.Implicits.global

object PRChecks extends Checks[GHPRRequest[_]] {

  val EmailVerified = check(_.userEmail.isVerified) or (req => s"Verify your email address (${req.userEmail.getEmail}) with GitHub")

  val UserNameExists = check(_.user.name.exists(_.length>1)) or "Set a Name in your GitHub profile - it'll be used as a display name next to your email"

  val UserOwnsPR = check(_.userOwnsPR) or (req => s"This PR was raised by ${req.pr.getUser.atLogin} - you can't submit it for them") // fatal

  val PRIsOpen = check(_.pr.getState == GHIssueState.OPEN) or "Can't submit a closed pull request - reopen it if you're sure"

  val HasBeenPreviewed = check(req => PreviewSignatures.hasPreviewed(req.pr)(req)) or (req => s"You need to preview these commits in a test email to yourself first - click the link at the bottom of the email!")


  def checkForSubmission(req: GHPRRequest[_]) = {
    /*
    * You did not create the PR
* The PR is not Open?
* You have not sent a preview email?
* We're out of SES quota
* User needs a Full Name in their GitHub profile (for the email and sign-off)
* Signed off missing - need a full name?
* The body of the commit messages is too short??? There are some commits in Git with just a subject line and "Signed-off-by"s
* The subject line is too long (can't do for whole body, some things can go long!)
* You have not registered your email with Amazon SES
     */

    for (commitsAndPatches <- req.commitsAndPatchesF) {
      assert(commitsAndPatches.length < 10)
      for ((commit, patch) <- commitsAndPatches) {
        assert(patch.subject.length > 10 && patch.subject.length < 72)
      }
    }
    for (verificationStatus <- ses.getIdentityVerificationStatusFor(req.user.primaryEmail.getEmail)) {
      assert(verificationStatus == "Success")
    }
  }
}
