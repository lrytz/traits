package org.scalalang.traits.backend

import org.scalalang.traits.backend.ai.{EnrichService, LlmClient, StubLlmClient}
import org.scalalang.traits.backend.auth.{AuthApi, SessionCodec}
import org.scalalang.traits.backend.topic.{TopicApi, TopicService}
import org.scalalang.traits.shared.Endpoints
import ox.*
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.files.{staticFilesGetServerEndpoint, FilesOptions}
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.netty.sync.NettySyncServer

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

    val topics = TopicService(ds)
    if topics.count() == 0 then Seed.run(topics)

    val codec = SessionCodec(cfg.sessionSecret.getBytes(UTF_8))
    val auth  = AuthApi(codec, cfg.editorPassword, sessionTtl.getSeconds, cfg.cookieSecure)

    val llm: LlmClient = StubLlmClient()
    val enrich         = EnrichService(llm)
    val topicApi       = TopicApi(auth, topics, enrich)

    val health: ServerEndpoint[Any, Identity] =
      Endpoints.health.handleSuccess(_ => Endpoints.Health("ok", topics.count()))

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
      .addEndpoints(List(health) ++ auth.all ++ topicApi.all ++ List(staticFrontend))
      .startAndWait()
