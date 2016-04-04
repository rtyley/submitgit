package lib.model

import java.time.ZonedDateTime
import javax.mail.internet.InternetAddress

import com.madgag.scalagithub.model.CommitIdent
import com.madgag.scalagithub.model.PullRequest.CommitOverview
import lib.Email.Addresses
import org.eclipse.jgit.lib.ObjectId
import org.scalatestplus.play.PlaySpec

class PatchBombSpec extends PlaySpec {

  val bob = CommitIdent("bob", "bob@x.com", ZonedDateTime.now)
  val fred = CommitIdent("fred", "fred@y.com", ZonedDateTime.now)

  def patchCommitAuthoredBy(author: CommitIdent) =
    PatchCommit(Patch(ObjectId.zeroId, "PATCHBODY"),
      CommitOverview("", ObjectId.zeroId, "", CommitOverview.Commit(
        "", author, author, "Commit message", 0
      ))
    )


  def patchBombFrom(user: CommitIdent, patchCommit: PatchCommit) = PatchBomb(
    Seq(patchCommit),
    Addresses(from = new InternetAddress(user.addressString)),
    footer = "FOOTER"
  )
  
  "Patch bomb" should {
    "add a in-body 'From' header when commit author differs from email sender" in {
      val patchCommit = patchCommitAuthoredBy(bob)
      val patchBomb = patchBombFrom(fred, patchCommit)

      patchBomb.emails.head.bodyText must startWith(s"From: bob <bob@x.com>\n\n${patchCommit.patch.body}")
    }

    "not add an in-body 'From' header when the commit author matches the email sender" in {
      val patchBomb = patchBombFrom(bob, patchCommitAuthoredBy(bob))

      patchBomb.emails.head.bodyText must not include "From:"
    }

    "not add an in-body 'From' header when the commit author used a 'noreply' address" in {
      // http://article.gmane.org/gmane.comp.version-control.git/286879
      val mrNoReply = CommitIdent("Peter Dave Hello", "peterdavehello@users.noreply.github.com", ZonedDateTime.now)
      val patchBomb = patchBombFrom(bob, patchCommitAuthoredBy(mrNoReply))

      val text = patchBomb.emails.head.bodyText

      text must not include "From:"
    }
  }
}
