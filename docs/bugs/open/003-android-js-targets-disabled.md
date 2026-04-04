## 🐛 BUG-003: Android and JS Targets Disabled [SEVERITY: Medium]

**Status**: 🔄 Partial Fix
**Discovered**: 2026-01-11 during Project Analysis
**Impact**: Multiplatform capabilities are currently limited to JVM/Desktop and iOS. Android and JS targets are disabled in build configuration.

**Reproduction**:
1. Check `kmp/build.gradle.kts`
2. Observe `if (project.findProperty("enableJs") == "true")` and `if (project.findProperty("enableAndroid") == "true")` blocks
3. Check `gradle.properties` and see these properties are not set to true

**Root Cause**:
- **Android**: Incompatibility between Kotlin Compiler (2.0.21) and SQLDelight plugin (2.0.2) generated code IR lowering.
- **JS**: `OutOfMemoryError` and Node.js binary resolution failures during configuration.

**Files Affected** (2 files):
- `kmp/build.gradle.kts` - Targets conditionally enabled
- `gradle.properties` - Flags missing

**Fix Approach**:
1. Investigate SQLDelight compatibility with Kotlin 2.0.21 for Android.
2. Fix memory settings and Node.js configuration for JS target.
3. Re-enable targets in `gradle.properties`.

**Progress**:
- **Android**: ✅ FIXED - Added `DriverFactory.android.kt` with proper SQLDelight AndroidSqliteDriver initialization
- **JS**: ❌ Not started - Requires missing `PlatformFileSystem.js.kt` and System import fixes

**Files Modified**:
- `kmp/src/androidMain/kotlin/com/logseq/kmp/db/DriverFactory.android.kt` - NEW
- `gradle.properties` - Added `enableJs=true`

**Files Still Needed for JS**:
- `kmp/src/jsMain/kotlin/com/logseq/kmp/platform/PlatformFileSystem.js.kt`
- Fix `System` references in common code (use expect/actual)

**Verification**:
- [x] Run `./gradlew :kmp:compileDebugKotlinAndroid` successfully.
- [ ] Run `./gradlew :kmp:compileKotlinJs` successfully.

**Related Tasks**: 
- KMP Migration
