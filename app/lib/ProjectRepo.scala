package lib

import lib.github.RepoName

case class MailingList(emailAddress: String, archives: Seq[MailArchive])

object Project {
  val Git = Project(
    RepoName("git", "git"),
    MailingList("git@vger.kernel.org", Seq(
      Gmane.Git
      // MailArchiveDotCom("git@vger.kernel.org") -- message-id search appears broken
    ))
  )
  val PretendGit = Project(
    RepoName("submitgit", "pretend-git"),
    GoogleGroup("submitgit-test").mailingList
  )

  val all = Set(Git, PretendGit)

  val byRepoName = all.map(p => p.repoId -> p).toMap
}

case class Project(repoId: RepoName, mailingList: MailingList)
