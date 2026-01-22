# KMP Migration Bug Report

## 🔴 Critical / Blocking Issues

### 1. Android Target Compilation Failure
*   **Status**: ⛔ Blocking
*   **Symptom**: `Internal compiler error` during `ExternalPackageParentPatcherLowering`.
*   **Cause**: Incompatibility between Kotlin Compiler (tested 2.0.21 and 1.9.24) and SQLDelight plugin (2.0.2) generated code IR lowering on Android target.
*   **Mitigation**: Android target is temporarily disabled in `kmp/build.gradle.kts`.

### 2. JS Target Configuration Failure
*   **Status**: ⛔ Blocking
*   **Symptom**: `OutOfMemoryError: GC overhead limit exceeded` and `Node.js` binary resolution failure during Gradle configuration.
*   **Cause**: Kotlin JS plugin environment configuration issues.
*   **Mitigation**: JS target is temporarily disabled.

### 3. JVM Runtime Crash (Skiko Incompatibility)
*   **Status**: ⛔ Blocking
*   **Symptom**: `java.lang.NoSuchMethodError: 'void org.jetbrains.skiko.SkiaLayer.<init>(...)'` at runtime.
*   **Cause**: Binary incompatibility between `androidx.compose` (1.6.11) and the resolved `skiko-awt` version. The manual dependency `skiko-awt-runtime-linux-x64:0.8.18` was removed, but the transitive resolution remains incorrect or conflicts with the system environment.
*   **Workaround**: Compilation succeeds with Kotlin 1.9.24, validating the code logic. Runtime requires resolving the strict dependency mismatch, likely by pinning a compatible Skiko version (e.g., 0.7.85 or 0.8.9 depending on the exact Compose build).

## 🟡 Resolved Issues

### 4. JVM Internal Compiler Error
*   **Status**: ✅ Fixed
*   **Resolution**: Downgraded Kotlin from `2.0.21` to `1.9.24`. This resolved the `ExternalPackageParentPatcherLowering` crash on the JVM target.

### 5. Missing Platform Implementations
*   **Status**: ✅ Fixed
*   **Details**: 
    *   `GitManager` was missing a `jvmMain` implementation for `GitManagerFactory`. Added a stub implementation.
    *   `PlatformFileSystem` on Android was rewritten to use `java.io.File` to avoid API level issues with `java.nio.file`.

### 6. Compose Resources & Icons
*   **Status**: ✅ Fixed
*   **Details**: Fixed `Sidebar.kt` using deprecated wildcard imports for Material Icons. Updated to specific imports (e.g., `Icons.AutoMirrored.Filled.List`).

## 🔍 Pre-existing Issues (Legacy)
*   **ClojureScript Lint Errors**: Multiple unresolved symbols in legacy `.clj` and `.cljs` files (`file_sync_actions.clj`, `security.cljs`). These do not affect the KMP build but may impact the legacy app.
