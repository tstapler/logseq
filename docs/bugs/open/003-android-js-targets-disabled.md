## 🐛 BUG-003: Android and JS Targets Disabled [SEVERITY: Medium]

**Status**: 🐛 Open
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

**Verification**:
- Run `./gradlew :kmp:compileKotlinAndroid` successfully.
- Run `./gradlew :kmp:compileKotlinJs` successfully.

**Related Tasks**: 
- KMP Migration
