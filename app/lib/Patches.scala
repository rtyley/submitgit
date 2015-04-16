package lib

import org.eclipse.jgit.lib.ObjectId

object Patches {

  val PatchStartRegex = """^From (\p{XDigit}{40}) """.r.unanchored

  def commitsAndPatches(commits: Seq[ObjectId], githubPatch: String): Seq[(ObjectId, Patch)] = {
    val linesWithSeparators = githubPatch.linesWithSeparators.toList

    val commitsByPatchStart = linesWithSeparators.zipWithIndex.collect {
      case (PatchStartRegex(commitId), index) => index -> ObjectId.fromString(commitId)
    }.toMap

    val patchStarts = commitsByPatchStart.keys.toSeq.sorted

    for (patchStartAndEnd <- (patchStarts :+ linesWithSeparators.size).sliding(2).toSeq if patchStartAndEnd.size == 2) yield {
      val commit = commitsByPatchStart(patchStartAndEnd.head)
      commit -> Patch(linesWithSeparators.slice(patchStartAndEnd.head, patchStartAndEnd.last))
    }
  }

}
