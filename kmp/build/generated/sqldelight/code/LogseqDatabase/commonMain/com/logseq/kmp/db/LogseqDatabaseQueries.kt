package com.logseq.kmp.db

import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import kotlin.Any
import kotlin.Long
import kotlin.String
import kotlin.collections.Collection

public class LogseqDatabaseQueries(
  driver: SqlDriver,
) : TransacterImpl(driver) {
  public fun <T : Any> selectBlockByUuid(uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlockByUuidQuery(uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlockByUuid(uuid: String): Query<Blocks> = selectBlockByUuid(uuid, ::Blocks)

  public fun existsBlockByUuid(uuid: String): Query<Long> = ExistsBlockByUuidQuery(uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectAllBlocks(mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = Query(-1_541_457_519, arrayOf("blocks"), driver, "LogseqDatabase.sq", "selectAllBlocks", "SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks ORDER BY uuid") { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectAllBlocks(): Query<Blocks> = selectAllBlocks(::Blocks)

  public fun <T : Any> selectAllBlocksPaginated(
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectAllBlocksPaginatedQuery(value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectAllBlocksPaginated(value_: Long, value__: Long): Query<Blocks> = selectAllBlocksPaginated(value_, value__, ::Blocks)

  public fun <T : Any> selectBlockChildren(
    parent_uuid: String?,
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectBlockChildrenQuery(parent_uuid, value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlockChildren(
    parent_uuid: String?,
    value_: Long,
    value__: Long,
  ): Query<Blocks> = selectBlockChildren(parent_uuid, value_, value__, ::Blocks)

  public fun countBlockChildren(parent_uuid: String?): Query<Long> = CountBlockChildrenQuery(parent_uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectBlockSiblings(
    uuid: String,
    uuid_: String,
    uuid__: String,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectBlockSiblingsQuery(uuid, uuid_, uuid__) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlockSiblings(
    uuid: String,
    uuid_: String,
    uuid__: String,
  ): Query<Blocks> = selectBlockSiblings(uuid, uuid_, uuid__, ::Blocks)

  public fun <T : Any> selectRootBlocks(
    page_uuid: String,
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectRootBlocksQuery(page_uuid, value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectRootBlocks(
    page_uuid: String,
    value_: Long,
    value__: Long,
  ): Query<Blocks> = selectRootBlocks(page_uuid, value_, value__, ::Blocks)

  public fun countRootBlocks(page_uuid: String): Query<Long> = CountRootBlocksQuery(page_uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectBlocksByPageUuid(
    page_uuid: String,
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectBlocksByPageUuidQuery(page_uuid, value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksByPageUuid(
    page_uuid: String,
    value_: Long,
    value__: Long,
  ): Query<Blocks> = selectBlocksByPageUuid(page_uuid, value_, value__, ::Blocks)

  public fun <T : Any> selectBlocksByPageUuidUnpaginated(page_uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksByPageUuidUnpaginatedQuery(page_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksByPageUuidUnpaginated(page_uuid: String): Query<Blocks> = selectBlocksByPageUuidUnpaginated(page_uuid, ::Blocks)

  public fun <T : Any> selectBlocksWithContentLike(content: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksWithContentLikeQuery(content) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksWithContentLike(content: String): Query<Blocks> = selectBlocksWithContentLike(content, ::Blocks)

  public fun <T : Any> selectBlocksWithContentLikePaginated(
    content: String,
    `value`: Long,
    value_: Long,
    mapper: (
      id: Long,
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      content_hash: String?,
    ) -> T,
  ): Query<T> = SelectBlocksWithContentLikePaginatedQuery(content, value, value_) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksWithContentLikePaginated(
    content: String,
    value_: Long,
    value__: Long,
  ): Query<Blocks> = selectBlocksWithContentLikePaginated(content, value_, value__, ::Blocks)

  public fun countBlocksByPageUuid(page_uuid: String): Query<Long> = CountBlocksByPageUuidQuery(page_uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectBlocksByParentUuidOrdered(parent_uuid: String?, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksByParentUuidOrderedQuery(parent_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksByParentUuidOrdered(parent_uuid: String?): Query<Blocks> = selectBlocksByParentUuidOrdered(parent_uuid, ::Blocks)

  public fun <T : Any> selectBlocksByParentUuids(parent_uuid: Collection<String?>, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksByParentUuidsQuery(parent_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksByParentUuids(parent_uuid: Collection<String?>): Query<Blocks> = selectBlocksByParentUuids(parent_uuid, ::Blocks)

  public fun <T : Any> selectRootBlocksByPageUuidOrdered(page_uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectRootBlocksByPageUuidOrderedQuery(page_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectRootBlocksByPageUuidOrdered(page_uuid: String): Query<Blocks> = selectRootBlocksByPageUuidOrdered(page_uuid, ::Blocks)

  public fun <T : Any> selectBlockByLeftUuid(left_uuid: String?, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlockByLeftUuidQuery(left_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlockByLeftUuid(left_uuid: String?): Query<Blocks> = selectBlockByLeftUuid(left_uuid, ::Blocks)

  public fun <T : Any> selectLastChild(parent_uuid: String?, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectLastChildQuery(parent_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectLastChild(parent_uuid: String?): Query<Blocks> = selectLastChild(parent_uuid, ::Blocks)

  public fun countBlocks(): Query<Long> = Query(-1_047_477_477, arrayOf("blocks"), driver, "LogseqDatabase.sq", "countBlocks", "SELECT COUNT(*) FROM blocks") { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectBlocksByContentHash(content_hash: String?, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksByContentHashQuery(content_hash) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksByContentHash(content_hash: String?): Query<Blocks> = selectBlocksByContentHash(content_hash, ::Blocks)

  public fun <T : Any> selectDuplicateBlockHashes(`value`: Long, mapper: (content_hash: String, cnt: Long) -> T): Query<T> = SelectDuplicateBlockHashesQuery(value) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getLong(1)!!
    )
  }

  public fun selectDuplicateBlockHashes(value_: Long): Query<SelectDuplicateBlockHashes> = selectDuplicateBlockHashes(value_, ::SelectDuplicateBlockHashes)

  public fun <T : Any> selectPageByUuid(uuid: String, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectPageByUuidQuery(uuid) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPageByUuid(uuid: String): Query<Pages> = selectPageByUuid(uuid, ::Pages)

  public fun <T : Any> selectPageByName(name: String, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectPageByNameQuery(name) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPageByName(name: String): Query<Pages> = selectPageByName(name, ::Pages)

  public fun existsPageByUuid(uuid: String): Query<Long> = ExistsPageByUuidQuery(uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun existsPageByName(name: String): Query<Long> = ExistsPageByNameQuery(name) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectAllPages(mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = Query(1_902_532_185, arrayOf("pages"), driver, "LogseqDatabase.sq", "selectAllPages", "SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages ORDER BY name") { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectAllPages(): Query<Pages> = selectAllPages(::Pages)

  public fun <T : Any> selectAllPagesPaginated(
    `value`: Long,
    value_: Long,
    mapper: (
      uuid: String,
      name: String,
      namespace: String?,
      file_path: String?,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      is_favorite: Long?,
      is_journal: Long?,
      journal_date: String?,
    ) -> T,
  ): Query<T> = SelectAllPagesPaginatedQuery(value, value_) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectAllPagesPaginated(value_: Long, value__: Long): Query<Pages> = selectAllPagesPaginated(value_, value__, ::Pages)

  public fun <T : Any> selectPagesByNamespace(
    namespace: String?,
    `value`: Long,
    value_: Long,
    mapper: (
      uuid: String,
      name: String,
      namespace: String?,
      file_path: String?,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      is_favorite: Long?,
      is_journal: Long?,
      journal_date: String?,
    ) -> T,
  ): Query<T> = SelectPagesByNamespaceQuery(namespace, value, value_) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPagesByNamespace(
    namespace: String?,
    value_: Long,
    value__: Long,
  ): Query<Pages> = selectPagesByNamespace(namespace, value_, value__, ::Pages)

  public fun <T : Any> selectPagesByNamespaceUnpaginated(namespace: String?, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectPagesByNamespaceUnpaginatedQuery(namespace) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPagesByNamespaceUnpaginated(namespace: String?): Query<Pages> = selectPagesByNamespaceUnpaginated(namespace, ::Pages)

  public fun countPagesByNamespace(namespace: String?): Query<Long> = CountPagesByNamespaceQuery(namespace) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectRecentlyUpdatedPages(`value`: Long, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectRecentlyUpdatedPagesQuery(value) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectRecentlyUpdatedPages(value_: Long): Query<Pages> = selectRecentlyUpdatedPages(value_, ::Pages)

  public fun <T : Any> selectRecentlyCreatedPages(`value`: Long, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectRecentlyCreatedPagesQuery(value) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectRecentlyCreatedPages(value_: Long): Query<Pages> = selectRecentlyCreatedPages(value_, ::Pages)

  public fun countPages(): Query<Long> = Query(-298_290_289, arrayOf("pages"), driver, "LogseqDatabase.sq", "countPages", "SELECT COUNT(*) FROM pages") { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> selectJournalPages(
    `value`: Long,
    value_: Long,
    mapper: (
      uuid: String,
      name: String,
      namespace: String?,
      file_path: String?,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      is_favorite: Long?,
      is_journal: Long?,
      journal_date: String?,
    ) -> T,
  ): Query<T> = SelectJournalPagesQuery(value, value_) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectJournalPages(value_: Long, value__: Long): Query<Pages> = selectJournalPages(value_, value__, ::Pages)

  public fun <T : Any> selectOutgoingReferences(from_block_uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectOutgoingReferencesQuery(from_block_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectOutgoingReferences(from_block_uuid: String): Query<Blocks> = selectOutgoingReferences(from_block_uuid, ::Blocks)

  public fun <T : Any> selectIncomingReferences(to_block_uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectIncomingReferencesQuery(to_block_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectIncomingReferences(to_block_uuid: String): Query<Blocks> = selectIncomingReferences(to_block_uuid, ::Blocks)

  public fun <T : Any> selectOrphanedBlocks(mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = Query(2_037_006_019, arrayOf("blocks", "block_references"), driver, "LogseqDatabase.sq", "selectOrphanedBlocks", """
  |SELECT b.id, b.uuid, b.page_uuid, b.parent_uuid, b.left_uuid, b.content, b.level, b.position, b.created_at, b.updated_at, b.properties, b.version, b.content_hash FROM blocks b
  |LEFT JOIN block_references br ON b.uuid = br.to_block_uuid
  |WHERE br.id IS NULL
  """.trimMargin()) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectOrphanedBlocks(): Query<Blocks> = selectOrphanedBlocks(::Blocks)

  public fun <T : Any> selectMostConnectedBlocks(`value`: Long, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
    reference_count: Long,
  ) -> T): Query<T> = SelectMostConnectedBlocksQuery(value) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12),
      cursor.getLong(13)!!
    )
  }

  public fun selectMostConnectedBlocks(value_: Long): Query<SelectMostConnectedBlocks> = selectMostConnectedBlocks(value_, ::SelectMostConnectedBlocks)

  public fun <T : Any> selectPagesByNameLike(name: String, mapper: (
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ) -> T): Query<T> = SelectPagesByNameLikeQuery(name) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPagesByNameLike(name: String): Query<Pages> = selectPagesByNameLike(name, ::Pages)

  public fun <T : Any> selectPagesByNameLikePaginated(
    name: String,
    `value`: Long,
    value_: Long,
    mapper: (
      uuid: String,
      name: String,
      namespace: String?,
      file_path: String?,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      is_favorite: Long?,
      is_journal: Long?,
      journal_date: String?,
    ) -> T,
  ): Query<T> = SelectPagesByNameLikePaginatedQuery(name, value, value_) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getLong(4)!!,
      cursor.getLong(5)!!,
      cursor.getString(6),
      cursor.getLong(7)!!,
      cursor.getLong(8),
      cursor.getLong(9),
      cursor.getString(10)
    )
  }

  public fun selectPagesByNameLikePaginated(
    name: String,
    value_: Long,
    value__: Long,
  ): Query<Pages> = selectPagesByNameLikePaginated(name, value_, value__, ::Pages)

  public fun <T : Any> selectBlocksReferencing(to_block_uuid: String, mapper: (
    id: Long,
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ) -> T): Query<T> = SelectBlocksReferencingQuery(to_block_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3),
      cursor.getString(4),
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getLong(9)!!,
      cursor.getString(10),
      cursor.getLong(11)!!,
      cursor.getString(12)
    )
  }

  public fun selectBlocksReferencing(to_block_uuid: String): Query<Blocks> = selectBlocksReferencing(to_block_uuid, ::Blocks)

  public fun <T : Any> selectPluginDataById(id: Long, mapper: (
    id: Long,
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
    value_: String,
    created_at: Long,
    updated_at: Long?,
  ) -> T): Query<T> = SelectPluginDataByIdQuery(id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)
    )
  }

  public fun selectPluginDataById(id: Long): Query<Plugin_data> = selectPluginDataById(id, ::Plugin_data)

  public fun <T : Any> selectPluginDataByPlugin(plugin_id: String, mapper: (
    id: Long,
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
    value_: String,
    created_at: Long,
    updated_at: Long?,
  ) -> T): Query<T> = SelectPluginDataByPluginQuery(plugin_id) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)
    )
  }

  public fun selectPluginDataByPlugin(plugin_id: String): Query<Plugin_data> = selectPluginDataByPlugin(plugin_id, ::Plugin_data)

  public fun <T : Any> selectPluginDataByEntity(
    entity_type: String,
    entity_uuid: String,
    mapper: (
      id: Long,
      plugin_id: String,
      entity_type: String,
      entity_uuid: String,
      key: String,
      value_: String,
      created_at: Long,
      updated_at: Long?,
    ) -> T,
  ): Query<T> = SelectPluginDataByEntityQuery(entity_type, entity_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)
    )
  }

  public fun selectPluginDataByEntity(entity_type: String, entity_uuid: String): Query<Plugin_data> = selectPluginDataByEntity(entity_type, entity_uuid, ::Plugin_data)

  public fun <T : Any> selectPluginDataByKey(
    plugin_id: String,
    key: String,
    mapper: (
      id: Long,
      plugin_id: String,
      entity_type: String,
      entity_uuid: String,
      key: String,
      value_: String,
      created_at: Long,
      updated_at: Long?,
    ) -> T,
  ): Query<T> = SelectPluginDataByKeyQuery(plugin_id, key) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)
    )
  }

  public fun selectPluginDataByKey(plugin_id: String, key: String): Query<Plugin_data> = selectPluginDataByKey(plugin_id, key, ::Plugin_data)

  public fun <T : Any> selectPluginDataByPluginAndEntity(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    mapper: (
      id: Long,
      plugin_id: String,
      entity_type: String,
      entity_uuid: String,
      key: String,
      value_: String,
      created_at: Long,
      updated_at: Long?,
    ) -> T,
  ): Query<T> = SelectPluginDataByPluginAndEntityQuery(plugin_id, entity_type, entity_uuid) { cursor ->
    mapper(
      cursor.getLong(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2)!!,
      cursor.getString(3)!!,
      cursor.getString(4)!!,
      cursor.getString(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)
    )
  }

  public fun selectPluginDataByPluginAndEntity(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
  ): Query<Plugin_data> = selectPluginDataByPluginAndEntity(plugin_id, entity_type, entity_uuid, ::Plugin_data)

  public fun countPluginDataByPlugin(plugin_id: String): Query<Long> = CountPluginDataByPluginQuery(plugin_id) { cursor ->
    cursor.getLong(0)!!
  }

  public fun countPluginDataByEntity(entity_type: String, entity_uuid: String): Query<Long> = CountPluginDataByEntityQuery(entity_type, entity_uuid) { cursor ->
    cursor.getLong(0)!!
  }

  public fun existsPluginData(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
  ): Query<Long> = ExistsPluginDataQuery(plugin_id, entity_type, entity_uuid, key) { cursor ->
    cursor.getLong(0)!!
  }

  public fun <T : Any> searchBlocksByContentFts(
    query: String,
    limit: Long,
    offset: Long,
    mapper: (
      uuid: String,
      page_uuid: String,
      parent_uuid: String?,
      left_uuid: String?,
      content: String,
      level: Long,
      position: Long,
      created_at: Long,
      updated_at: Long,
      properties: String?,
      version: Long,
      highlight: String?,
    ) -> T,
  ): Query<T> = SearchBlocksByContentFtsQuery(query, limit, offset) { cursor ->
    mapper(
      cursor.getString(0)!!,
      cursor.getString(1)!!,
      cursor.getString(2),
      cursor.getString(3),
      cursor.getString(4)!!,
      cursor.getLong(5)!!,
      cursor.getLong(6)!!,
      cursor.getLong(7)!!,
      cursor.getLong(8)!!,
      cursor.getString(9),
      cursor.getLong(10)!!,
      cursor.getString(11)
    )
  }

  public fun searchBlocksByContentFts(
    query: String,
    limit: Long,
    offset: Long,
  ): Query<SearchBlocksByContentFts> = searchBlocksByContentFts(query, limit, offset, ::SearchBlocksByContentFts)

  public fun searchBlocksCountFts(query: String): Query<Long> = SearchBlocksCountFtsQuery(query) { cursor ->
    cursor.getLong(0)!!
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockParent(parent_uuid: String?, uuid: String): QueryResult<Long> {
    val result = driver.execute(-996_487_628, """UPDATE blocks SET parent_uuid = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, parent_uuid)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(-996_487_628) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockParentPositionAndLevel(
    parent_uuid: String?,
    position: Long,
    level: Long,
    uuid: String,
  ): QueryResult<Long> {
    val result = driver.execute(461_418_122, """UPDATE blocks SET parent_uuid = ?, position = ?, level = ? WHERE uuid = ?""", 4) {
          var parameterIndex = 0
          bindString(parameterIndex++, parent_uuid)
          bindLong(parameterIndex++, position)
          bindLong(parameterIndex++, level)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(461_418_122) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockHierarchy(
    parent_uuid: String?,
    left_uuid: String?,
    position: Long,
    level: Long,
    uuid: String,
  ): QueryResult<Long> {
    val result = driver.execute(495_937_643, """UPDATE blocks SET parent_uuid = ?, left_uuid = ?, position = ?, level = ? WHERE uuid = ?""", 5) {
          var parameterIndex = 0
          bindString(parameterIndex++, parent_uuid)
          bindString(parameterIndex++, left_uuid)
          bindLong(parameterIndex++, position)
          bindLong(parameterIndex++, level)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(495_937_643) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockPositionOnly(position: Long, uuid: String): QueryResult<Long> {
    val result = driver.execute(-1_033_279_873, """UPDATE blocks SET position = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, position)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(-1_033_279_873) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockContent(
    content: String,
    updated_at: Long,
    uuid: String,
  ): QueryResult<Long> {
    val result = driver.execute(918_560_815, """UPDATE blocks SET content = ?, updated_at = ?, version = version + 1 WHERE uuid = ?""", 3) {
          var parameterIndex = 0
          bindString(parameterIndex++, content)
          bindLong(parameterIndex++, updated_at)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(918_560_815) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockLevelOnly(level: Long, uuid: String): QueryResult<Long> {
    val result = driver.execute(1_019_388_806, """UPDATE blocks SET level = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, level)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(1_019_388_806) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockLeftUuid(left_uuid: String?, uuid: String): QueryResult<Long> {
    val result = driver.execute(696_399_724, """UPDATE blocks SET left_uuid = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, left_uuid)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(696_399_724) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updateBlockProperties(properties: String?, uuid: String): QueryResult<Long> {
    val result = driver.execute(418_086_333, """UPDATE blocks SET properties = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, properties)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(418_086_333) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteBlockByUuid(uuid: String): QueryResult<Long> {
    val result = driver.execute(-138_254_310, """DELETE FROM blocks WHERE uuid = ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(-138_254_310) { emit ->
      emit("block_references")
      emit("blocks")
      emit("blocks_fts")
      emit("properties")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteBlockChildren(parent_uuid: String?): QueryResult<Long> {
    val result = driver.execute(null, """DELETE FROM blocks WHERE parent_uuid ${ if (parent_uuid == null) "IS" else "=" } ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, parent_uuid)
        }
    notifyQueries(385_882_983) { emit ->
      emit("block_references")
      emit("blocks")
      emit("blocks_fts")
      emit("properties")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteAllBlocks(): QueryResult<Long> {
    val result = driver.execute(-215_597_630, """DELETE FROM blocks""", 0)
    notifyQueries(-215_597_630) { emit ->
      emit("block_references")
      emit("blocks")
      emit("blocks_fts")
      emit("properties")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteBlocksByPageUuid(page_uuid: String): QueryResult<Long> {
    val result = driver.execute(1_939_078_892, """DELETE FROM blocks WHERE page_uuid = ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, page_uuid)
        }
    notifyQueries(1_939_078_892) { emit ->
      emit("block_references")
      emit("blocks")
      emit("blocks_fts")
      emit("properties")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertBlock(
    uuid: String,
    page_uuid: String,
    parent_uuid: String?,
    left_uuid: String?,
    content: String,
    level: Long,
    position: Long,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    content_hash: String?,
  ): QueryResult<Long> {
    val result = driver.execute(1_684_331_770, """
        |INSERT INTO blocks (uuid, page_uuid, parent_uuid, left_uuid, content, level, position, created_at, updated_at, properties, version, content_hash)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 12) {
          var parameterIndex = 0
          bindString(parameterIndex++, uuid)
          bindString(parameterIndex++, page_uuid)
          bindString(parameterIndex++, parent_uuid)
          bindString(parameterIndex++, left_uuid)
          bindString(parameterIndex++, content)
          bindLong(parameterIndex++, level)
          bindLong(parameterIndex++, position)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, updated_at)
          bindString(parameterIndex++, properties)
          bindLong(parameterIndex++, version)
          bindString(parameterIndex++, content_hash)
        }
    notifyQueries(1_684_331_770) { emit ->
      emit("blocks")
      emit("blocks_fts")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePageProperties(properties: String?, uuid: String): QueryResult<Long> {
    val result = driver.execute(1_404_293_477, """UPDATE pages SET properties = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, properties)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(1_404_293_477) { emit ->
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePageName(name: String, uuid: String): QueryResult<Long> {
    val result = driver.execute(895_831_101, """UPDATE pages SET name = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(895_831_101) { emit ->
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePage(
    namespace: String?,
    file_path: String?,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
    uuid: String,
  ): QueryResult<Long> {
    val result = driver.execute(455_210_642, """
        |UPDATE pages SET 
        |    namespace = ?, 
        |    file_path = ?, 
        |    updated_at = ?, 
        |    properties = ?, 
        |    version = ?, 
        |    is_favorite = ?, 
        |    is_journal = ?, 
        |    journal_date = ?
        |WHERE uuid = ?
        """.trimMargin(), 9) {
          var parameterIndex = 0
          bindString(parameterIndex++, namespace)
          bindString(parameterIndex++, file_path)
          bindLong(parameterIndex++, updated_at)
          bindString(parameterIndex++, properties)
          bindLong(parameterIndex++, version)
          bindLong(parameterIndex++, is_favorite)
          bindLong(parameterIndex++, is_journal)
          bindString(parameterIndex++, journal_date)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(455_210_642) { emit ->
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deletePageByUuid(uuid: String): QueryResult<Long> {
    val result = driver.execute(1_340_979_270, """DELETE FROM pages WHERE uuid = ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(1_340_979_270) { emit ->
      emit("blocks")
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertPage(
    uuid: String,
    name: String,
    namespace: String?,
    file_path: String?,
    created_at: Long,
    updated_at: Long,
    properties: String?,
    version: Long,
    is_favorite: Long?,
    is_journal: Long?,
    journal_date: String?,
  ): QueryResult<Long> {
    val result = driver.execute(1_717_307_522, """
        |INSERT OR IGNORE INTO pages (uuid, name, namespace, file_path, created_at, updated_at, properties, version, is_favorite, is_journal, journal_date)
        |VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 11) {
          var parameterIndex = 0
          bindString(parameterIndex++, uuid)
          bindString(parameterIndex++, name)
          bindString(parameterIndex++, namespace)
          bindString(parameterIndex++, file_path)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, updated_at)
          bindString(parameterIndex++, properties)
          bindLong(parameterIndex++, version)
          bindLong(parameterIndex++, is_favorite)
          bindLong(parameterIndex++, is_journal)
          bindString(parameterIndex++, journal_date)
        }
    notifyQueries(1_717_307_522) { emit ->
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePageFavorite(is_favorite: Long?, uuid: String): QueryResult<Long> {
    val result = driver.execute(-28_347_826, """UPDATE pages SET is_favorite = ? WHERE uuid = ?""", 2) {
          var parameterIndex = 0
          bindLong(parameterIndex++, is_favorite)
          bindString(parameterIndex++, uuid)
        }
    notifyQueries(-28_347_826) { emit ->
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteAllPages(): QueryResult<Long> {
    val result = driver.execute(-1_102_739_448, """DELETE FROM pages""", 0)
    notifyQueries(-1_102_739_448) { emit ->
      emit("blocks")
      emit("pages")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertBlockReference(
    from_block_uuid: String,
    to_block_uuid: String,
    created_at: Long,
  ): QueryResult<Long> {
    val result = driver.execute(962_100_081, """
        |INSERT OR REPLACE INTO block_references (from_block_uuid, to_block_uuid, created_at)
        |VALUES (?, ?, ?)
        """.trimMargin(), 3) {
          var parameterIndex = 0
          bindString(parameterIndex++, from_block_uuid)
          bindString(parameterIndex++, to_block_uuid)
          bindLong(parameterIndex++, created_at)
        }
    notifyQueries(962_100_081) { emit ->
      emit("block_references")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deleteBlockReference(from_block_uuid: String, to_block_uuid: String): QueryResult<Long> {
    val result = driver.execute(-1_753_403_677, """DELETE FROM block_references WHERE from_block_uuid = ? AND to_block_uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, from_block_uuid)
          bindString(parameterIndex++, to_block_uuid)
        }
    notifyQueries(-1_753_403_677) { emit ->
      emit("block_references")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun insertPluginData(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
    value_: String,
    created_at: Long,
    updated_at: Long?,
  ): QueryResult<Long> {
    val result = driver.execute(1_888_036_144, """
        |INSERT INTO plugin_data (plugin_id, entity_type, entity_uuid, key, value, created_at, updated_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          var parameterIndex = 0
          bindString(parameterIndex++, plugin_id)
          bindString(parameterIndex++, entity_type)
          bindString(parameterIndex++, entity_uuid)
          bindString(parameterIndex++, key)
          bindString(parameterIndex++, value_)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, updated_at)
        }
    notifyQueries(1_888_036_144) { emit ->
      emit("plugin_data")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun updatePluginData(
    value_: String,
    updated_at: Long?,
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
  ): QueryResult<Long> {
    val result = driver.execute(386_418_496, """UPDATE plugin_data SET value = ?, updated_at = ? WHERE plugin_id = ? AND entity_type = ? AND entity_uuid = ? AND key = ?""", 6) {
          var parameterIndex = 0
          bindString(parameterIndex++, value_)
          bindLong(parameterIndex++, updated_at)
          bindString(parameterIndex++, plugin_id)
          bindString(parameterIndex++, entity_type)
          bindString(parameterIndex++, entity_uuid)
          bindString(parameterIndex++, key)
        }
    notifyQueries(386_418_496) { emit ->
      emit("plugin_data")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun upsertPluginData(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
    value_: String,
    created_at: Long,
    updated_at: Long?,
  ): QueryResult<Long> {
    val result = driver.execute(-1_223_270_362, """
        |INSERT OR REPLACE INTO plugin_data (plugin_id, entity_type, entity_uuid, key, value, created_at, updated_at)
        |VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimMargin(), 7) {
          var parameterIndex = 0
          bindString(parameterIndex++, plugin_id)
          bindString(parameterIndex++, entity_type)
          bindString(parameterIndex++, entity_uuid)
          bindString(parameterIndex++, key)
          bindString(parameterIndex++, value_)
          bindLong(parameterIndex++, created_at)
          bindLong(parameterIndex++, updated_at)
        }
    notifyQueries(-1_223_270_362) { emit ->
      emit("plugin_data")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deletePluginData(
    plugin_id: String,
    entity_type: String,
    entity_uuid: String,
    key: String,
  ): QueryResult<Long> {
    val result = driver.execute(149_250_466, """DELETE FROM plugin_data WHERE plugin_id = ? AND entity_type = ? AND entity_uuid = ? AND key = ?""", 4) {
          var parameterIndex = 0
          bindString(parameterIndex++, plugin_id)
          bindString(parameterIndex++, entity_type)
          bindString(parameterIndex++, entity_uuid)
          bindString(parameterIndex++, key)
        }
    notifyQueries(149_250_466) { emit ->
      emit("plugin_data")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deletePluginDataByPlugin(plugin_id: String): QueryResult<Long> {
    val result = driver.execute(1_825_476_140, """DELETE FROM plugin_data WHERE plugin_id = ?""", 1) {
          var parameterIndex = 0
          bindString(parameterIndex++, plugin_id)
        }
    notifyQueries(1_825_476_140) { emit ->
      emit("plugin_data")
    }
    return result
  }

  /**
   * @return The number of rows updated.
   */
  public fun deletePluginDataByEntity(entity_type: String, entity_uuid: String): QueryResult<Long> {
    val result = driver.execute(1_512_375_004, """DELETE FROM plugin_data WHERE entity_type = ? AND entity_uuid = ?""", 2) {
          var parameterIndex = 0
          bindString(parameterIndex++, entity_type)
          bindString(parameterIndex++, entity_uuid)
        }
    notifyQueries(1_512_375_004) { emit ->
      emit("plugin_data")
    }
    return result
  }

  private inner class SelectBlockByUuidQuery<out T : Any>(
    public val uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_315_679_273, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlockByUuid"
  }

  private inner class ExistsBlockByUuidQuery<out T : Any>(
    public val uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_098_542_903, """SELECT COUNT(*) FROM blocks WHERE uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:existsBlockByUuid"
  }

  private inner class SelectAllBlocksPaginatedQuery<out T : Any>(
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-95_859_652, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks ORDER BY uuid LIMIT ? OFFSET ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectAllBlocksPaginated"
  }

  private inner class SelectBlockChildrenQuery<out T : Any>(
    public val parent_uuid: String?,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid ${ if (parent_uuid == null) "IS" else "=" } ? ORDER BY position LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, parent_uuid)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlockChildren"
  }

  private inner class CountBlockChildrenQuery<out T : Any>(
    public val parent_uuid: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT COUNT(*) FROM blocks WHERE parent_uuid ${ if (parent_uuid == null) "IS" else "=" } ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, parent_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:countBlockChildren"
  }

  private inner class SelectBlockSiblingsQuery<out T : Any>(
    public val uuid: String,
    public val uuid_: String,
    public val uuid__: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(259_124_136, """
    |SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks 
    |WHERE parent_uuid IS (SELECT parent_uuid FROM blocks WHERE uuid = ?) 
    |AND page_uuid = (SELECT page_uuid FROM blocks WHERE uuid = ?)
    |AND uuid != ? 
    |ORDER BY position
    """.trimMargin(), mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, uuid)
      bindString(parameterIndex++, uuid_)
      bindString(parameterIndex++, uuid__)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlockSiblings"
  }

  private inner class SelectRootBlocksQuery<out T : Any>(
    public val page_uuid: String,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(440_428_350, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid IS NULL AND page_uuid = ? ORDER BY position LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectRootBlocks"
  }

  private inner class CountRootBlocksQuery<out T : Any>(
    public val page_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_971_104_797, """SELECT COUNT(*) FROM blocks WHERE parent_uuid IS NULL AND page_uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:countRootBlocks"
  }

  private inner class SelectBlocksByPageUuidQuery<out T : Any>(
    public val page_uuid: String,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(28_175_421, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE page_uuid = ? ORDER BY position LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksByPageUuid"
  }

  private inner class SelectBlocksByPageUuidUnpaginatedQuery<out T : Any>(
    public val page_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_573_266_391, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE page_uuid = ? ORDER BY position""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksByPageUuidUnpaginated"
  }

  private inner class SelectBlocksWithContentLikeQuery<out T : Any>(
    public val content: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(355_075_246, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE content LIKE ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, content)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksWithContentLike"
  }

  private inner class SelectBlocksWithContentLikePaginatedQuery<out T : Any>(
    public val content: String,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-41_635_393, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE content LIKE ? ORDER BY created_at DESC LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, content)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksWithContentLikePaginated"
  }

  private inner class CountBlocksByPageUuidQuery<out T : Any>(
    public val page_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_010_093_532, """SELECT COUNT(*) FROM blocks WHERE page_uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:countBlocksByPageUuid"
  }

  private inner class SelectBlocksByParentUuidOrderedQuery<out T : Any>(
    public val parent_uuid: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid ${ if (parent_uuid == null) "IS" else "=" } ? ORDER BY position""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, parent_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksByParentUuidOrdered"
  }

  private inner class SelectBlocksByParentUuidsQuery<out T : Any>(
    public val parent_uuid: Collection<String?>,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
      val parent_uuidIndexes = createArguments(count = parent_uuid.size)
      return driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid IN $parent_uuidIndexes ORDER BY parent_uuid, position""", mapper, parent_uuid.size) {
            var parameterIndex = 0
            parent_uuid.forEach { parent_uuid_ ->
              bindString(parameterIndex++, parent_uuid_)
            }
          }
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksByParentUuids"
  }

  private inner class SelectRootBlocksByPageUuidOrderedQuery<out T : Any>(
    public val page_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_560_184_530, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid IS NULL AND page_uuid = ? ORDER BY position""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, page_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectRootBlocksByPageUuidOrdered"
  }

  private inner class SelectBlockByLeftUuidQuery<out T : Any>(
    public val left_uuid: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE left_uuid ${ if (left_uuid == null) "IS" else "=" } ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, left_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlockByLeftUuid"
  }

  private inner class SelectLastChildQuery<out T : Any>(
    public val parent_uuid: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE parent_uuid ${ if (parent_uuid == null) "IS" else "=" } ? ORDER BY position DESC LIMIT 1""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, parent_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectLastChild"
  }

  private inner class SelectBlocksByContentHashQuery<out T : Any>(
    public val content_hash: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT blocks.id, blocks.uuid, blocks.page_uuid, blocks.parent_uuid, blocks.left_uuid, blocks.content, blocks.level, blocks.position, blocks.created_at, blocks.updated_at, blocks.properties, blocks.version, blocks.content_hash FROM blocks WHERE content_hash ${ if (content_hash == null) "IS" else "=" } ? ORDER BY created_at""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, content_hash)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksByContentHash"
  }

  private inner class SelectDuplicateBlockHashesQuery<out T : Any>(
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-394_560_044, """
    |SELECT content_hash, COUNT(*) AS cnt
    |FROM blocks
    |WHERE content_hash IS NOT NULL
    |GROUP BY content_hash
    |HAVING COUNT(*) > 1
    |ORDER BY cnt DESC
    |LIMIT ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectDuplicateBlockHashes"
  }

  private inner class SelectPageByUuidQuery<out T : Any>(
    public val uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_105_971_625, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPageByUuid"
  }

  private inner class SelectPageByNameQuery<out T : Any>(
    public val name: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_106_199_257, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE name = ? LIMIT 1""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, name)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPageByName"
  }

  private inner class ExistsPageByUuidQuery<out T : Any>(
    public val uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_725_644_215, """SELECT COUNT(*) FROM pages WHERE uuid = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:existsPageByUuid"
  }

  private inner class ExistsPageByNameQuery<out T : Any>(
    public val name: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_725_416_583, """SELECT COUNT(*) FROM pages WHERE name = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, name)
    }

    override fun toString(): String = "LogseqDatabase.sq:existsPageByName"
  }

  private inner class SelectAllPagesPaginatedQuery<out T : Any>(
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(245_502_068, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages ORDER BY name LIMIT ? OFFSET ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectAllPagesPaginated"
  }

  private inner class SelectPagesByNamespaceQuery<out T : Any>(
    public val namespace: String?,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE namespace ${ if (namespace == null) "IS" else "=" } ? ORDER BY name LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, namespace)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPagesByNamespace"
  }

  private inner class SelectPagesByNamespaceUnpaginatedQuery<out T : Any>(
    public val namespace: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE namespace ${ if (namespace == null) "IS" else "=" } ? ORDER BY name""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, namespace)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPagesByNamespaceUnpaginated"
  }

  private inner class CountPagesByNamespaceQuery<out T : Any>(
    public val namespace: String?,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(null, """SELECT COUNT(*) FROM pages WHERE namespace ${ if (namespace == null) "IS" else "=" } ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, namespace)
    }

    override fun toString(): String = "LogseqDatabase.sq:countPagesByNamespace"
  }

  private inner class SelectRecentlyUpdatedPagesQuery<out T : Any>(
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_535_742_009, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages ORDER BY updated_at DESC LIMIT ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectRecentlyUpdatedPages"
  }

  private inner class SelectRecentlyCreatedPagesQuery<out T : Any>(
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(940_287_418, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages ORDER BY created_at DESC LIMIT ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectRecentlyCreatedPages"
  }

  private inner class SelectJournalPagesQuery<out T : Any>(
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_845_912_451, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE is_journal = 1 ORDER BY COALESCE(journal_date, name) DESC LIMIT ? OFFSET ?""", mapper, 2) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectJournalPages"
  }

  private inner class SelectOutgoingReferencesQuery<out T : Any>(
    public val from_block_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", "block_references", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", "block_references", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(28_945_226, """
    |SELECT b.id, b.uuid, b.page_uuid, b.parent_uuid, b.left_uuid, b.content, b.level, b.position, b.created_at, b.updated_at, b.properties, b.version, b.content_hash FROM blocks b
    |INNER JOIN block_references br ON b.uuid = br.to_block_uuid
    |WHERE br.from_block_uuid = ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, from_block_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectOutgoingReferences"
  }

  private inner class SelectIncomingReferencesQuery<out T : Any>(
    public val to_block_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", "block_references", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", "block_references", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_108_955_012, """
    |SELECT b.id, b.uuid, b.page_uuid, b.parent_uuid, b.left_uuid, b.content, b.level, b.position, b.created_at, b.updated_at, b.properties, b.version, b.content_hash FROM blocks b
    |INNER JOIN block_references br ON b.uuid = br.from_block_uuid
    |WHERE br.to_block_uuid = ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, to_block_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectIncomingReferences"
  }

  private inner class SelectMostConnectedBlocksQuery<out T : Any>(
    public val `value`: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", "block_references", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", "block_references", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_368_319_606, """
    |SELECT b.id, b.uuid, b.page_uuid, b.parent_uuid, b.left_uuid, b.content, b.level, b.position, b.created_at, b.updated_at, b.properties, b.version, b.content_hash, COUNT(br.id) AS reference_count
    |FROM blocks b
    |LEFT JOIN block_references br ON b.uuid = br.to_block_uuid OR b.uuid = br.from_block_uuid
    |GROUP BY b.uuid
    |ORDER BY reference_count DESC
    |LIMIT ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, value)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectMostConnectedBlocks"
  }

  private inner class SelectPagesByNameLikeQuery<out T : Any>(
    public val name: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(549_520_551, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE name LIKE ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, name)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPagesByNameLike"
  }

  private inner class SelectPagesByNameLikePaginatedQuery<out T : Any>(
    public val name: String,
    public val `value`: Long,
    public val value_: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("pages", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("pages", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_573_624_294, """SELECT pages.uuid, pages.name, pages.namespace, pages.file_path, pages.created_at, pages.updated_at, pages.properties, pages.version, pages.is_favorite, pages.is_journal, pages.journal_date FROM pages WHERE name LIKE ? ORDER BY name LIMIT ? OFFSET ?""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, name)
      bindLong(parameterIndex++, value)
      bindLong(parameterIndex++, value_)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPagesByNameLikePaginated"
  }

  private inner class SelectBlocksReferencingQuery<out T : Any>(
    public val to_block_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", "block_references", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", "block_references", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(289_462_508, """
    |SELECT DISTINCT b.id, b.uuid, b.page_uuid, b.parent_uuid, b.left_uuid, b.content, b.level, b.position, b.created_at, b.updated_at, b.properties, b.version, b.content_hash FROM blocks b
    |INNER JOIN block_references br ON b.uuid = br.from_block_uuid
    |WHERE br.to_block_uuid = ?
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, to_block_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectBlocksReferencing"
  }

  private inner class SelectPluginDataByIdQuery<out T : Any>(
    public val id: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_241_423_397, """SELECT plugin_data.id, plugin_data.plugin_id, plugin_data.entity_type, plugin_data.entity_uuid, plugin_data.key, plugin_data.value, plugin_data.created_at, plugin_data.updated_at FROM plugin_data WHERE id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindLong(parameterIndex++, id)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPluginDataById"
  }

  private inner class SelectPluginDataByPluginQuery<out T : Any>(
    public val plugin_id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-601_724_099, """SELECT plugin_data.id, plugin_data.plugin_id, plugin_data.entity_type, plugin_data.entity_uuid, plugin_data.key, plugin_data.value, plugin_data.created_at, plugin_data.updated_at FROM plugin_data WHERE plugin_id = ? ORDER BY created_at""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, plugin_id)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPluginDataByPlugin"
  }

  private inner class SelectPluginDataByEntityQuery<out T : Any>(
    public val entity_type: String,
    public val entity_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-914_825_235, """SELECT plugin_data.id, plugin_data.plugin_id, plugin_data.entity_type, plugin_data.entity_uuid, plugin_data.key, plugin_data.value, plugin_data.created_at, plugin_data.updated_at FROM plugin_data WHERE entity_type = ? AND entity_uuid = ? ORDER BY key""", mapper, 2) {
      var parameterIndex = 0
      bindString(parameterIndex++, entity_type)
      bindString(parameterIndex++, entity_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPluginDataByEntity"
  }

  private inner class SelectPluginDataByKeyQuery<out T : Any>(
    public val plugin_id: String,
    public val key: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-170_578_283, """SELECT plugin_data.id, plugin_data.plugin_id, plugin_data.entity_type, plugin_data.entity_uuid, plugin_data.key, plugin_data.value, plugin_data.created_at, plugin_data.updated_at FROM plugin_data WHERE plugin_id = ? AND key = ? ORDER BY entity_type, entity_uuid""", mapper, 2) {
      var parameterIndex = 0
      bindString(parameterIndex++, plugin_id)
      bindString(parameterIndex++, key)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPluginDataByKey"
  }

  private inner class SelectPluginDataByPluginAndEntityQuery<out T : Any>(
    public val plugin_id: String,
    public val entity_type: String,
    public val entity_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-436_771, """SELECT plugin_data.id, plugin_data.plugin_id, plugin_data.entity_type, plugin_data.entity_uuid, plugin_data.key, plugin_data.value, plugin_data.created_at, plugin_data.updated_at FROM plugin_data WHERE plugin_id = ? AND entity_type = ? AND entity_uuid = ? ORDER BY key""", mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, plugin_id)
      bindString(parameterIndex++, entity_type)
      bindString(parameterIndex++, entity_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:selectPluginDataByPluginAndEntity"
  }

  private inner class CountPluginDataByPluginQuery<out T : Any>(
    public val plugin_id: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(-1_871_224_548, """SELECT COUNT(*) FROM plugin_data WHERE plugin_id = ?""", mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, plugin_id)
    }

    override fun toString(): String = "LogseqDatabase.sq:countPluginDataByPlugin"
  }

  private inner class CountPluginDataByEntityQuery<out T : Any>(
    public val entity_type: String,
    public val entity_uuid: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(2_110_641_612, """SELECT COUNT(*) FROM plugin_data WHERE entity_type = ? AND entity_uuid = ?""", mapper, 2) {
      var parameterIndex = 0
      bindString(parameterIndex++, entity_type)
      bindString(parameterIndex++, entity_uuid)
    }

    override fun toString(): String = "LogseqDatabase.sq:countPluginDataByEntity"
  }

  private inner class ExistsPluginDataQuery<out T : Any>(
    public val plugin_id: String,
    public val entity_type: String,
    public val entity_uuid: String,
    public val key: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("plugin_data", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("plugin_data", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(533_915_411, """SELECT COUNT(*) FROM plugin_data WHERE plugin_id = ? AND entity_type = ? AND entity_uuid = ? AND key = ?""", mapper, 4) {
      var parameterIndex = 0
      bindString(parameterIndex++, plugin_id)
      bindString(parameterIndex++, entity_type)
      bindString(parameterIndex++, entity_uuid)
      bindString(parameterIndex++, key)
    }

    override fun toString(): String = "LogseqDatabase.sq:existsPluginData"
  }

  private inner class SearchBlocksByContentFtsQuery<out T : Any>(
    public val query: String,
    public val limit: Long,
    public val offset: Long,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks", "blocks_fts", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks", "blocks_fts", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(1_166_358_635, """
    |SELECT
    |    b.uuid,
    |    b.page_uuid,
    |    b.parent_uuid,
    |    b.left_uuid,
    |    b.content,
    |    b.level,
    |    b.position,
    |    b.created_at,
    |    b.updated_at,
    |    b.properties,
    |    b.version,
    |    highlight(blocks_fts, 2, '<em>', '</em>') AS highlight
    |FROM blocks_fts bm
    |JOIN blocks b ON b.id = bm.rowid
    |WHERE blocks_fts MATCH ? || '*'
    |ORDER BY b.id
    |LIMIT ? OFFSET ?
    """.trimMargin(), mapper, 3) {
      var parameterIndex = 0
      bindString(parameterIndex++, query)
      bindLong(parameterIndex++, limit)
      bindLong(parameterIndex++, offset)
    }

    override fun toString(): String = "LogseqDatabase.sq:searchBlocksByContentFts"
  }

  private inner class SearchBlocksCountFtsQuery<out T : Any>(
    public val query: String,
    mapper: (SqlCursor) -> T,
  ) : Query<T>(mapper) {
    override fun addListener(listener: Query.Listener) {
      driver.addListener("blocks_fts", listener = listener)
    }

    override fun removeListener(listener: Query.Listener) {
      driver.removeListener("blocks_fts", listener = listener)
    }

    override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> = driver.executeQuery(726_968_414, """
    |SELECT COUNT(*) AS result_count
    |FROM blocks_fts bm
    |WHERE blocks_fts MATCH ? || '*'
    """.trimMargin(), mapper, 1) {
      var parameterIndex = 0
      bindString(parameterIndex++, query)
    }

    override fun toString(): String = "LogseqDatabase.sq:searchBlocksCountFts"
  }
}
