package lib

import com.madgag.git._
import com.madgag.scalagithub.model.PullRequest
import org.eclipse.jgit.lib.ObjectId
import play.api.libs.Crypto
import play.api.mvc.RequestHeader


object PreviewSignatures {
  def keyFor(headCommit: ObjectId) = s"previewed_${headCommit.name}"

  def signatureFor(headCommit: ObjectId) = Crypto.sign(keyFor(headCommit))

  def hasPreviewed(pr: PullRequest)(implicit req: RequestHeader): Boolean = {
    val headCommit = pr.head.sha
    req.session.get(keyFor(headCommit)).exists(sig => Crypto.constantTimeEquals(sig, signatureFor(headCommit)))
  }
}
