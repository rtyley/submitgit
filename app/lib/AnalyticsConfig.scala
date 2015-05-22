package lib


object AnalyticsConfig {

  import play.api.Play.current
  val config = play.api.Play.configuration

  val googleAnalyticsIdOpt = config.getString("google.analytics.id")
}
