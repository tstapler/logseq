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

## Kotlin Coroutines and Flow Best Practices (KMP)

### Coroutines

1. **Inject Dispatchers** - Never hardcode `Dispatchers.Default` or `Dispatchers.IO`. Inject them as constructor parameters for testability:
   ```kotlin
   class Repository(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)
   ```

2. **Suspend functions must be main-safe** - Classes doing blocking work should use `withContext` internally, not callers.

3. **ViewModel creates coroutines** - Use `viewModelScope.launch {}` in ViewModels rather than exposing suspend functions.

4. **Don't expose mutable types** - Expose `StateFlow<T>` not `MutableStateFlow<T>`:
   ```kotlin
   private val _uiState = MutableStateFlow(UiState.Loading)
   val uiState: StateFlow<UiState> = _uiState
   ```

5. **Avoid GlobalScope** - Inject `CoroutineScope` as a parameter instead.

6. **Make coroutines cancellable** - Use `ensureActive()` in loops for blocking operations.

7. **Exception handling** - Catch specific exceptions, always rethrow `CancellationException`.

8. **coroutineScope vs supervisorScope** - Use `coroutineScope` for screen-lifecycle work (parallel operations), external scope for app-lifetime work.

### Flow

1. **Flows are cold and lazy** - Producer code runs each time `collect` is called.

2. **Use `flowOn` for context** - Affects upstream operations:
   ```kotlin
   flow.map { transform(it) }
       .flowOn(Dispatchers.Default)  // map runs on Default
       .collect { }  // collect runs on caller's context
   ```

3. **Use `catch` for exceptions** - Handle producer errors, can emit fallback values.

4. **Use `callbackFlow` for callbacks** - When emitting from different contexts or callback-based APIs.

5. **Share flows with `shareIn`** - Avoid duplicate producer work for multiple collectors.

6. **Data layer** - Expose `suspend fun` for one-shot calls, `Flow<T>` for data that changes over time.

## Review Checklist
- Linters and unit-tests must pass
- Check the review notes listed in `prompts/review.md`.

## Operational Limitations
- **Background Processes**: The agent environment does not support running persistent background processes (like `runApp` or watchers). Always request the user to execute these commands manually if they need to run alongside other tasks.
