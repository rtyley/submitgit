package lib.model

import com.madgag.scalagithub.model.PullRequest.CommitOverview
import fastparse.all._

case class PatchCommit(patch: Patch, commit: CommitOverview)

object PatchCommit {
  def from(commits: Seq[CommitOverview], githubPatch: String): Seq[PatchCommit] = {

    val Parsed.Success(patches, s) = PatchParsing.patches.parse(githubPatch)

    val commitsById = commits.map(c => c.sha -> c).toMap

    patches.map(patch => PatchCommit(patch, commitsById(patch.commitId)))
  }
}