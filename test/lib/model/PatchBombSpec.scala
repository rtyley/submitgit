package lib.model

import javax.mail.internet.InternetAddress

import lib.Email.Addresses
import org.eclipse.jgit.lib.ObjectId
import org.scalatestplus.play.PlaySpec

class PatchBombSpec extends PlaySpec {

  val bob = UserIdent("bob", "bob@x.com")
  val fred = UserIdent("fred", "fred@y.com")

  def patchCommitAuthoredBy(author: UserIdent) =
    PatchCommit(Patch(ObjectId.zeroId, "PATCHBODY"), Commit(ObjectId.zeroId, author, author, "COMMITMESSAGE"))


  def patchBombFrom(user: UserIdent, patchCommit: PatchCommit) = PatchBomb(
    Seq(patchCommit),
    Addresses(from = new InternetAddress(user.userEmailString)),
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
      val mrNoReply = UserIdent("Peter Dave Hello", "peterdavehello@users.noreply.github.com")
      val patchBomb = patchBombFrom(bob, patchCommitAuthoredBy(mrNoReply))

      val text = patchBomb.emails.head.bodyText

      text must not include "From:"
    }
  }
}
