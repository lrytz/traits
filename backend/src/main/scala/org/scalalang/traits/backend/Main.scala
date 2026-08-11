package org.scalalang.traits.backend

import org.scalalang.traits.backend.auth.{AuthApi, SessionCodec}
import org.scalalang.traits.backend.entry.{EntryApi, EntryService}
import org.scalalang.traits.backend.version.{VersionApi, VersionService}
import org.scalalang.traits.shared.Endpoints
import ox.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.files.{staticFilesGetServerEndpoint, FilesOptions}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths
import java.time.Duration

object Main extends OxApp.Simple:

  private val sessionTtl = Duration.ofDays(30)

  override def run(using Ox): Unit =
    Logging.init()
    val cfg = AppConfig.load()
    val ds  = Db.dataSource(cfg.db)
    Db.migrate(ds)

    val entries  = EntryService(ds)
    val versions = VersionService(ds)

    val codec = SessionCodec(cfg.sessionSecret.getBytes(UTF_8))
    val auth  = AuthApi(codec, cfg.editorPassword, sessionTtl.getSeconds, cfg.cookieSecure)

    val entryApi   = EntryApi(auth, entries)
    val versionApi = VersionApi(auth, versions)

    val health: ServerEndpoint[Any, Identity] =
      Endpoints.health.handleSuccess(_ => Endpoints.Health("ok", entries.count()))

    val apiEndpoints: List[ServerEndpoint[Any, Identity]] =
      List(health) ++ auth.all ++ entryApi.all ++ versionApi.all

    // Live OpenAPI + Swagger UI at /docs (spec at /docs/docs.yaml), kept in sync with the served
    // endpoints. This is the contract external coding agents read to curate the data over HTTP.
    val docs: List[ServerEndpoint[Any, Identity]] =
      SwaggerInterpreter().fromServerEndpoints[Identity](apiEndpoints, "Traits API", "0.2.0")

    val staticDir = Paths.get(cfg.staticFilesPath).toAbsolutePath
    val staticFrontend: ServerEndpoint[Any, Identity] =
      staticFilesGetServerEndpoint[Identity](emptyInput)(
        staticDir.toString,
        FilesOptions.default.defaultFile(List("index.html"))
      )

    scribe.info(s"Starting traits on :${cfg.httpPort} (DB ${cfg.db.path}, static $staticDir)")

    val _ = NettySyncServer()
      .host("0.0.0.0")
      .port(cfg.httpPort)
      .addEndpoints(apiEndpoints ++ docs ++ List(staticFrontend))
      .startAndWait()
