## Repository Layout
- `src/`: Core source code
  - `src/main/`: The core logic of the application
    - `src/main/mobile/`: Mobile app code
    - `src/main/frontend/inference_worker/`: Code running in a webworker for text-embedding and vector-search
    - `src/main/frontend/worker/`: Code running in an another webworker
        - `src/main/frontend/worker/rtc/`: RTC(Real Time Collaboration) related code
    - `src/main/frontend/components/`: UI components
  - `src/electron/`: Code specifically for the Electron desktop application.
  - `src/test/`: unit-tests
- `deps/`: Internal dependencies/modules
- `clj-e2e/`: End to end test code
- `kmp/`: Kotlin Multiplatform code (New core logic)

## Migration to Kotlin Multiplatform (KMP)
We are migrating all of our logic to the Kotlin Multiplatform app. As functionality is transferred to the KMP implementation, the corresponding ClojureScript implementation should be deleted to avoid duplication and confusion.

## Architecture Documentation

- **Hybrid Database Architecture**: See [docs/ARCHITECTURE_DATASCRIPT_SQLITE.md](docs/ARCHITECTURE_DATASCRIPT_SQLITE.md)
  - Explains Datascript (in-memory) + SQLite (persistence) dual-database design
  - Documents the data flow from markdown files to Datascript to SQLite
  - Provides context for KMP migration decisions

## Common used cljs keywords
- All commonly used ClojureScript keywords are defined using `logseq.common.defkeywords/defkeyword`.
- Search for `defkeywords` to find all the definitions.

## Testing Commands
- Run linters and unit-tests: `bb dev:lint-and-test`
- Run single focused unit-test:
  - Add the `:focus` keyword to the test case: `(deftest ^:focus test-name ...)`
  - `bb dev:test -i focus`
- E2E tests files are located in `/clj-e2e`

## Common used cljs keywords
- All commonly used ClojureScript keywords are defined using `logseq.common.defkeywords/defkeyword`.
- Search for `defkeywords` to find all the definitions.

## Code Guidance
- Keep in mind: @prompts/review.md

## Review Checklist
- Linters and unit-tests must pass
- Check the review notes listed in `prompts/review.md`.

## Operational Limitations
- **Background Processes**: The agent environment does not support running persistent background processes (like `runApp` or watchers). Always request the user to execute these commands manually if they need to run alongside other tasks.
