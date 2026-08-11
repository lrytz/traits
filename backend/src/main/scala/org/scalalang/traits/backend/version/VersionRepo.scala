package org.scalalang.traits.backend.version

import org.scalalang.traits.shared.VersionId
import com.augustnagro.magnum.*

/** Raw access to the `version` registry table. `major`/`minor` are real columns so the natural
  * order is a SQL `ORDER BY`; the row itself is a JSON document like everything else.
  */
object VersionRepo:

  def findAll(using DbCon): List[String] =
    sql"SELECT data FROM version ORDER BY major, minor".query[String].run().toList

  def upsert(v: VersionId, data: String)(using DbCon): Unit =
    val _ = sql"""INSERT INTO version (major, minor, data)
                  VALUES (${v.major}, ${v.minor}, $data)
                  ON CONFLICT(major, minor) DO UPDATE SET data = excluded.data""".update.run()

  def delete(v: VersionId)(using DbCon): Int =
    sql"DELETE FROM version WHERE major = ${v.major} AND minor = ${v.minor}".update.run()
