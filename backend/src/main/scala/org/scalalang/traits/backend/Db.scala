package org.scalalang.traits.backend

import com.augustnagro.magnum.*
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import java.nio.file.{Files, Paths}
import javax.sql.DataSource

object Db:

  def dataSource(cfg: DbConfig): HikariDataSource =
    val parent = Paths.get(cfg.path).toAbsolutePath.getParent
    if parent != null && !Files.exists(parent) then
      val _ = Files.createDirectories(parent)
    val hc = HikariConfig()
    hc.setJdbcUrl(s"jdbc:sqlite:${cfg.path}")
    hc.setMaximumPoolSize(cfg.poolSize)
    hc.setPoolName("traits")
    // Wait rather than fail immediately when another connection holds the write lock.
    hc.setConnectionInitSql("PRAGMA busy_timeout=5000")
    HikariDataSource(hc)

  /** Idempotent schema setup. The store is a single document table: `data` holds the whole `Topic`
    * as JSON; `search_text` is a denormalised lower-cased blob for `LIKE` search. WAL mode lets
    * reads proceed while the occasional write commits. journal_mode must run outside a transaction,
    * so this uses `connect` (autocommit), not `transact`.
    */
  def migrate(ds: DataSource): Unit =
    connect(ds) {
      val _ = sql"PRAGMA journal_mode=WAL".query[String].run()
      val _ = sql"""CREATE TABLE IF NOT EXISTS topic (
                      slug        TEXT PRIMARY KEY,
                      updated_at  TEXT NOT NULL,
                      search_text TEXT NOT NULL,
                      data        TEXT NOT NULL
                    )""".update.run()
      val _ =
        sql"CREATE INDEX IF NOT EXISTS topic_updated_idx ON topic(updated_at)".update.run()
    }

  def topicCount(using DbCon): Long =
    sql"SELECT COUNT(*) FROM topic".query[Long].run().head
