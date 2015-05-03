package lib.github

object PullRequestId {
  def from(slug: String) = {
    val parts = slug.split('/')
    require(parts.length == 4)
    require(parts(2) == "pull")

    PullRequestId(RepoName(parts(0), parts(1)), parts(3).toInt)
  }
}


case class PullRequestId(repoName: RepoName, num: Int) {
  lazy val slug = s"${repoName.fullName}/pull/$num"
}
