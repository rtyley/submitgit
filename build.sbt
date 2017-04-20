name := "submitgit"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.11"

updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(
  PlayScala,
  BuildInfoPlugin
).settings(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    BuildInfoKey.constant("gitCommitId", Option(System.getenv("SOURCE_VERSION")) getOrElse(try {
      "git rev-parse HEAD".!!.trim
    } catch { case e: Exception => "unknown" }))
  ),
  buildInfoPackage := "app"
)

TwirlKeys.templateImports ++= Seq(
  "com.madgag.github.Implicits._",
  "com.madgag.scalagithub.model._",
  "lib.actions.Requests._"
)

routesImport ++= Seq("lib._","com.madgag.scalagithub.model._","controllers.Binders._","org.eclipse.jgit.lib.ObjectId")

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  cache,
  filters,
  "org.typelevel" %% "cats-core" % "0.9.0",
  "com.madgag" %% "play-git-hub" % "4.3",
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "org.webjars" % "bootstrap" % "3.3.7",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.5-P24",
  "org.webjars.bower" % "octicons" % "4.3.0",
  "org.webjars.bower" % "timeago" % "1.5.3",
  "org.webjars.bower" % "typeahead.js" % "0.11.1",
  "org.webjars.bower" % "typeahead.js-bootstrap3.less" % "0.2.3",
  "org.webjars.npm" % "handlebars" % "4.0.6",
  "org.jsoup" % "jsoup" % "1.10.2",
  "com.github.nscala-time" %% "nscala-time" % "2.16.0",
  "com.netaporter" %% "scala-uri" % "0.4.16",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.scalatestplus" %% "play" % "1.4.0" % "test",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.11.102",
  "com.sun.mail" % "javax.mail" % "1.5.6"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
