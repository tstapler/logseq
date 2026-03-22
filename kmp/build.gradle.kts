plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
    id("io.github.takahirom.roborazzi")
}

kotlin {
    jvmToolchain(21)
    applyDefaultHierarchyTemplate()

    // Configure targets
    jvm()

    if (project.findProperty("enableJs") == "true") {
        js(IR) {
            browser()
            binaries.executable()
        }
    }

    androidTarget()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // Configure source sets
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlinx libraries
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains:markdown:0.7.3")

                // SQLDelight
                implementation("app.cash.sqldelight:runtime:2.0.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")

                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)

                // Lifecycle
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("app.cash.sqldelight:sqlite-driver:2.0.2")

                // Graph databases for performance evaluation
                // implementation("com.kuzudb:kuzu-jdbc:0.7.0")
                // implementation("org.neo4j.driver:neo4j-java-driver:5.21.0")
                // implementation("org.neo4j:neo4j:5.21.0")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.desktop.uiTestJUnit4)
                implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop:1.59.0") {
                    exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4-desktop")
                }
                implementation("io.github.takahirom.roborazzi:roborazzi:1.59.0")
            }
        }

        if (project.findProperty("enableJs") == "true") {
            val jsMain by getting {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.8.1")
                    implementation(compose.html.core)
                    implementation("app.cash.sqldelight:web-worker-driver:2.0.2")
                }
            }

            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.2")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("androidx.core:core-ktx:1.13.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                implementation("app.cash.sqldelight:android-driver:2.0.2")

                // Compose BOM
                implementation(platform("androidx.compose:compose-bom:2024.09.03"))
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-graphics")
                implementation("androidx.compose.material3:material3")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
                implementation("androidx.arch.core:core-testing:2.2.0")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosx64:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-iosarm64:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-iossimulatorarm64:1.8.1")
                implementation("app.cash.sqldelight:native-driver:2.0.2")
            }
        }

        val iosTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }

        // Separate test source set for business logic (no UI dependencies)
        val businessTest by creating {
            dependsOn(commonTest)
        }

        jvmTest.dependsOn(businessTest)
        
        // Link iOS source sets
        // Access targets defined earlier in the kotlin block
        // targets.filter { it.platformType.name == "native" }.forEach {
        //      it.compilations.getByName("main").defaultSourceSet.dependsOn(iosMain)
        //      it.compilations.getByName("test").defaultSourceSet.dependsOn(iosTest)
        // }
    }
}

// Configure JVM test task for Compose Desktop UI tests
tasks.named<Test>("jvmTest") {
    jvmArgs("-Djava.awt.headless=false")
    // Enable software rendering for CI environments
    environment("LIBGL_ALWAYS_SOFTWARE", System.getenv("LIBGL_ALWAYS_SOFTWARE") ?: "")
    environment("GALLIUM_DRIVER", System.getenv("GALLIUM_DRIVER") ?: "")
}

compose.desktop {
    application {
        mainClass = "com.logseq.kmp.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb)
            packageName = "LogseqKMP"
            packageVersion = "1.0.0"
        }
    }
}

// Alias runApp to desktopRun for convenience
tasks.register("runApp") {
    dependsOn("run")
}

sqldelight {
    databases {
        create("LogseqDatabase") {
            packageName.set("com.logseq.kmp.db")
        }
    }
}

android {
    compileSdk = 35
    namespace = "com.logseq.kmp"

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
