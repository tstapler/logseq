# Atomic Tasks: Progressive Data Loading (Phase 1)

## Objective
Implement repository-level pagination for pages and blocks to support infinite scrolling and reduce memory pressure.

## Prerequisites
- [x] UUID-Native Block Storage ([DB-001](docs/tasks/uuid-native-block-storage.md))
- [x] Multi-Graph Support ([MG-001](docs/tasks/multi-graph-support.md))

## Atomic Tasks

### Task 1.1: Update PageRepository Interface (1h)
**Scope**: Add paginated query methods to the `PageRepository` interface.

**Files**:
- `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/GraphRepository.kt`

**Implementation**:
```kotlin
interface PageRepository {
    // ...
    fun getPages(limit: Int, offset: Int): Flow<Result<List<Page>>>
    fun searchPages(query: String, limit: Int, offset: Int): Flow<Result<List<Page>>>
}
```

**Validation**:
- Interface compiles.

**Status**: ✅ Completed

---

### Task 1.2: Implement Pagination in SqlDelightPageRepository (2h)
**Scope**: Implement the new interface methods using `LogseqDatabase.sq` queries.

**Files**:
- `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/SqlDelightPageRepository.kt`

**Implementation**:
- Use `queries.selectAllPagesPaginated(limit.toLong(), offset.toLong())`.
- Use `queries.selectPagesByNameLike("%$query%")` (existing) but apply `drop(offset).take(limit)` or add a new paginated query to `.sq`.

**Validation**:
- Unit test for `getPages` with different offsets and limits.

**Status**: ✅ Completed

---

### Task 1.3: Update BlockRepository Interface for Paginated References (1h)
**Scope**: Add paginated linked references method to `BlockRepository`.

**Files**:
- `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/GraphRepository.kt`

**Implementation**:
```kotlin
interface BlockRepository {
    // ...
    fun getLinkedReferences(pageName: String, limit: Int, offset: Int): Flow<Result<List<Block>>>
}
```

**Validation**:
- Interface compiles.

**Status**: ✅ Completed

---

### Task 1.4: Implement Paginated References in SqlDelightBlockRepository (2h)
**Scope**: Add SQL query for paginated references and implement in repository.

**Files**:
- `kmp/src/commonMain/sqldelight/com/logseq/kmp/db/LogseqDatabase.sq` (add query)
- `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/SqlDelightBlockRepository.kt`

**Implementation**:
- Add `selectBlocksWithContentLikePaginated` to `.sq`.
- Implement `getLinkedReferences` in `SqlDelightBlockRepository`.

**Validation**:
- Unit test with large number of references.

**Status**: ✅ Completed

---

## Dependency Visualization
```
Task 1.1 ──→ Task 1.2
Task 1.3 ──→ Task 1.4
```

## Next Step Recommendation
Start with **Task 1.1: Update PageRepository Interface** as it is the foundation for paginating the "All Pages" view.
