name := "submitgit"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.6"

updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(
  PlayScala,
  BuildInfoPlugin
).settings(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    BuildInfoKey.constant("gitCommitId", Option(System.getenv("BUILD_VCS_NUMBER")) getOrElse(try {
      "git rev-parse HEAD".!!.trim
    } catch { case e: Exception => "unknown" }))
  ),
  buildInfoPackage := "app"
)

TwirlKeys.templateImports ++= Seq(
  "com.madgag.github.Implicits._",
  "lib.actions.Requests._"
)

routesImport ++= Seq("lib._","com.madgag.github._","controllers.Binders._","org.eclipse.jgit.lib.ObjectId")

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  cache,
  filters,
  "com.madgag" %% "play-git-hub" % "1.1",
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "org.webjars" % "bootstrap" % "3.3.4",
  "org.webjars.bower" % "octicons" % "2.2.3",
  "com.lihaoyi" %% "fastparse" % "0.1.7",
  "com.github.nscala-time" %% "nscala-time" % "2.0.0",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.specs2" %% "specs2-core" % "2.4.17" % "test",
  "org.specs2" %% "specs2-junit" % "2.4.17" % "test",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.9.37",
  "com.sun.mail" % "javax.mail" % "1.5.3"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
