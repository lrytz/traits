package org.scalalang.traits.backend

case class DbConfig(path: String, poolSize: Int)

case class AppConfig(
    httpPort: Int,
    staticFilesPath: String,
    sessionSecret: String,
    editorPassword: String,
    cookieSecure: Boolean,
    db: DbConfig
)

object AppConfig:

  val isProd: Boolean        = sys.env.get("TRAITS_ENV").contains("prod")
  val isExplicitDev: Boolean = sys.env.get("TRAITS_ENV").contains("dev")

  /** Env var with a dev-only default. Fail-closed: required unless `TRAITS_ENV=dev` (which `sbt
    * backend/run` sets automatically). Guards the session secret and editor password from silently
    * falling back to public dev values in a misconfigured prod deploy.
    */
  private def dev(name: String, devDefault: String): String =
    sys.env.getOrElse(
      name,
      if isExplicitDev then devDefault
      else sys.error(s"$name is required (set TRAITS_ENV=dev to use the dev fallback)")
    )

  private def opt(name: String, default: String): String =
    sys.env.getOrElse(name, default)

  def load(): AppConfig =
    AppConfig(
      httpPort = opt("TRAITS_HTTP_PORT", "8080").toInt,
      staticFilesPath = opt("TRAITS_STATIC_FILES", "frontend/dist"),
      sessionSecret = dev("TRAITS_SESSION_SECRET", "dev-only-not-a-secret-rotate-in-prod"),
      editorPassword = dev("TRAITS_EDITOR_PASSWORD", "let-me-in"),
      cookieSecure = isProd,
      db = DbConfig(
        path = opt("TRAITS_DB_PATH", "traits-data/traits.sqlite"),
        poolSize = opt("TRAITS_DB_POOL_SIZE", "4").toInt
      )
    )
