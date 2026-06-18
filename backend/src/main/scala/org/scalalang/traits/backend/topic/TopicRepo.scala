package org.scalalang.traits.backend.topic

import com.augustnagro.magnum.*

/** Raw access to the single `topic` table. Returns the stored JSON document as a `String`; parsing
  * into the shared `Topic` is the service's job, keeping the repo free of upickle.
  */
object TopicRepo:

  def findAll(using DbCon): List[String] =
    sql"SELECT data FROM topic ORDER BY slug".query[String].run().toList

  def find(slug: String)(using DbCon): Option[String] =
    sql"SELECT data FROM topic WHERE slug = $slug".query[String].run().headOption

  def search(like: String)(using DbCon): List[String] =
    sql"SELECT data FROM topic WHERE search_text LIKE $like ORDER BY slug"
      .query[String]
      .run()
      .toList

  def upsert(slug: String, updatedAt: String, searchText: String, data: String)(using
      DbCon
  ): Unit =
    val _ = sql"""INSERT INTO topic (slug, updated_at, search_text, data)
                  VALUES ($slug, $updatedAt, $searchText, $data)
                  ON CONFLICT(slug) DO UPDATE SET
                    updated_at  = excluded.updated_at,
                    search_text = excluded.search_text,
                    data        = excluded.data""".update.run()

  def delete(slug: String)(using DbCon): Int =
    sql"DELETE FROM topic WHERE slug = $slug".update.run()
