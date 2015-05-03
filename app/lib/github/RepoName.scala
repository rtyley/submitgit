package lib.github

object RepoName {
  def from(fullName: String) = {
    val parts = fullName.split('/')
    require(parts.length == 2)

    RepoName(parts(0), parts(1))
  }
}

case class RepoName(owner: String, name: String) {

  require(!Seq(owner, name).exists(p => p.isEmpty || p.contains('/')))

  lazy val fullName = s"$owner/$name"
}
