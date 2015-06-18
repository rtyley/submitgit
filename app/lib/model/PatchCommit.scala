package lib.model

import fastparse._

case class PatchCommit(patch: Patch, commit: Commit)

object PatchCommit {
  def from(commits: Seq[Commit], githubPatch: String): Seq[PatchCommit] = {

    val Result.Success(patches, s) = PatchParsing.patches.parse(githubPatch)

    val commitsById = commits.map(c => c.id -> c).toMap

    patches.map(patch => PatchCommit(patch, commitsById(patch.commitId)))
  }
}