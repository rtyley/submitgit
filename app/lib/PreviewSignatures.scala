package lib

import org.eclipse.jgit.lib.ObjectId
import org.kohsuke.github.GHPullRequest
import play.api.libs.Crypto
import play.api.mvc.RequestHeader
import com.madgag.git._


object PreviewSignatures {
  def keyFor(headCommit: ObjectId) = s"previewed_${headCommit.name}"

  def signatureFor(headCommit: ObjectId) = Crypto.sign(keyFor(headCommit))

  def hasPreviewed(pr: GHPullRequest)(implicit req: RequestHeader): Boolean = {
    val headCommit = pr.getHead.getSha.asObjectId
    req.session.get(keyFor(headCommit)).exists(sig => Crypto.constantTimeEquals(sig, signatureFor(headCommit)))
  }
}
