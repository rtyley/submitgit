package lib.model

import lib.Email.Addresses
import org.eclipse.jgit.lib.ObjectId
import org.specs2.mutable.Specification

class PatchBombSpec extends Specification {
  val patchCommit = {
    val author = UserIdent("bob", "bob@x.com")
    PatchCommit(Patch(ObjectId.zeroId, "PATCHBODY"), Commit(ObjectId.zeroId, author, author, "COMMITMESSAGE"))
  }

  def patchBombFrom(text: String) = PatchBomb(
    Seq(patchCommit),
    Addresses(from = text),
    footer = "FOOTER"
  )
  
  "Patch bomb" should {
    "add a in-body 'From' header when commit author differs from email sender" in {
      patchBombFrom("fred <fred@y.com>").emails.head.bodyText must startWith(s"From: bob <bob@x.com>\n\n${patchCommit.patch.body}")
    }

    "not add an in-body 'From' header when the commit author matches the email sender" in {
      patchBombFrom("bob <bob@x.com>").emails.head.bodyText must not contain "From:"
    }

  }
}
