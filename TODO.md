# TODO.md - Logseq Kotlin Multiplatform Migration

## Current Status
- **Migration State**: Planning phase with detailed atomic task breakdown in `docs/tasks/kotlin-multiplatform-migration.md`
- **Technology Stack**: Currently ClojureScript + DataScript, planning migration to Kotlin Multiplatform
- **Recent Activity**: KMP foundation added with multiplatform architecture

## Active Work Streams

### 🚀 Epic: Kotlin Multiplatform Migration (Priority: HIGH)
**Goal**: Migrate from ClojureScript to Kotlin Multiplatform for better developer experience and cross-platform code reuse.

**Current Progress**: 0% complete
**Total Tasks**: 15+ atomic tasks across 3 stories
**Estimated Timeline**: 12 months
**Blockers**: None identified

#### Completed Tasks
- None

#### In Progress Tasks
- None

#### Pending Tasks
- [ ] **1.1 Analyze Current DataScript Schema** [2h] - Foundation task for data model migration
- [ ] **1.2 Design Kotlin Data Classes** [3h] - Create type-safe Kotlin equivalents
- [ ] **1.3 Implement Repository Pattern** [4h] - Replace DataScript queries
- [ ] **2.1 Analyze Current Rum/Reagent State** [2h] - Document state management patterns
- [ ] **2.2 Implement Kotlin State Management** [3h] - Create shared state with Flows
- [ ] **3.1 Component Analysis and Design** [4h] - Catalog UI components
- [ ] **3.2 Core Component Migration** [4h] - Migrate basic UI components
- [ ] ...additional tasks for mobile/desktop platforms

## Known Issues & Risks
- **HIGH**: Data migration complexity from DataScript to relational/SQL structures
- **MEDIUM**: Performance regression in graph traversal queries
- **LOW**: Plugin API compatibility during transition

## Next Recommended Action
**Priority**: IMMEDIATE
**Task**: 1.1 Analyze Current DataScript Schema [2h]
**Rationale**: This is the foundation task that must be completed before any other migration work can proceed. It establishes the baseline understanding of the current data model that all subsequent tasks depend on.
**Context Boundary**: 3 files max (schema definitions, current usage, documentation)
**Estimated Time**: 2 hours
**Deliverable**: Complete schema documentation and entity-relationship analysis

## Context Preparation
To work on the next task, load these files:
- `deps/db/src/logseq/db/file_based/schema.cljs` - Core schema definitions
- `deps/db/src/logseq/db/frontend/schema.cljs` - DB-specific schema extensions
- `src/main/frontend/db.cljs` - Current database usage patterns

## Success Criteria
- All core entities (Block, Page, Graph) documented with relationships
- CRUD operation patterns identified
- Data migration path outlined
- Query patterns mapped to potential SQL equivalents</content>
<parameter name="filePath">TODO.md