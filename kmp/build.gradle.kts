plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
    id("io.github.takahirom.roborazzi") version "1.59.0"
}

kotlin {
    jvmToolchain(21)
    applyDefaultHierarchyTemplate()

    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }

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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("org.jetbrains:markdown:0.7.3")

                // SQLDelight
                implementation("app.cash.sqldelight:runtime:2.3.2")
                implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")

                // Compose Multiplatform
                implementation("org.jetbrains.compose.runtime:runtime:1.7.3")
                implementation("org.jetbrains.compose.foundation:foundation:1.7.3")
                implementation("org.jetbrains.compose.material3:material3:1.7.3")
                implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
                implementation("org.jetbrains.compose.components:components-resources:1.7.3")

                // Lifecycle
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

                // OpenTelemetry API for common metrics
                implementation("io.opentelemetry:opentelemetry-api:1.43.0")
            }
        }

        val jvmCommonMain by creating {
            dependsOn(commonMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
            }
        }

        val jvmMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation("org.jetbrains.compose.desktop:desktop-jvm:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
                implementation("app.cash.sqldelight:sqlite-driver:2.3.2")

                // Graph databases for performance evaluation
                // implementation("com.kuzudb:kuzu-jdbc:0.7.0")
                // implementation("org.neo4j.driver:neo4j-java-driver:5.21.0")
                // implementation("org.neo4j:neo4j:5.21.0")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.7.3")
                implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop:1.59.0") {
                    exclude(group = "org.jetbrains.compose.ui", module = "ui-test-junit4-desktop")
                }
            }
        }

        if (project.findProperty("enableJs") == "true") {
            val jsMain by getting {
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-js:1.10.2")
                    implementation("org.jetbrains.compose.html:html-core:1.7.3")
                    implementation("app.cash.sqldelight:web-worker-driver:2.3.2")
                    implementation(npm("@cashapp/sqldelight-sqljs-worker", "2.3.2"))
                    implementation(npm("sql.js", "1.10.3"))
                    implementation(devNpm("copy-webpack-plugin", "9.1.0"))
                }
            }

            val jsTest by getting {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommonMain)
            dependencies {
                implementation("androidx.activity:activity-compose:1.13.0")
                implementation("androidx.appcompat:appcompat:1.7.0")
                implementation("androidx.core:core-ktx:1.15.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
                implementation("app.cash.sqldelight:android-driver:2.3.2")
                implementation("com.github.requery:sqlite-android:3.45.0")

                // Android Compose (Jetpack)
                implementation("androidx.compose.ui:ui:1.10.6")
                implementation("androidx.compose.ui:ui-graphics:1.10.6")
                implementation("androidx.compose.material3:material3:1.4.0")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("androidx.arch.core:core-testing:2.2.0")
            }
        }

        val iosMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("app.cash.sqldelight:native-driver:2.3.2")
            }
        }

        val iosTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
