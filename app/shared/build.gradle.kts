import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

// Settings baked into the app at build time. Set them in ~/.gradle/gradle.properties (or as
// environment variables in CI); docs/sign-in.md says where each value comes from. Left empty,
// the app still builds and works, and sign-in says it isn't set up yet.
val kinzoConfig = mapOf(
    // Constant to (Gradle property, environment variable).
    "API_URL" to ("kinzo.apiUrl" to "KINZO_API_URL"),
    "GOOGLE_WEB_CLIENT_ID" to ("kinzo.googleWebClientId" to "KINZO_GOOGLE_WEB_CLIENT_ID"),
).mapValues { (_, source) ->
    providers.gradleProperty(source.first)
        .orElse(providers.environmentVariable(source.second))
        .orElse("")
}

val generateKinzoConfig by tasks.registering {
    val values = kinzoConfig
    val outputDir = layout.buildDirectory.dir("generated/kinzoConfig/kotlin")
    values.forEach { (name, value) -> inputs.property(name, value) }
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("com/tomhartill/kinzo/KinzoConfig.kt").asFile
        file.parentFile.mkdirs()
        val fields = values.entries.joinToString("\n") { (name, value) ->
            val escaped = value.get().replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
            "    const val $name = \"$escaped\""
        }
        file.writeText(
            "package com.tomhartill.kinzo\n\n" +
                "/** Generated from Gradle properties by :shared:generateKinzoConfig. Don't edit. */\n" +
                "internal object KinzoConfig {\n$fields\n}\n",
        )
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // Desktop target: fast UI iteration on the Mac and the quickest way to run tests.
    jvm()

    androidLibrary {
        namespace = "com.tomhartill.kinzo.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.browser)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.playServices)
            implementation(libs.googleid)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.ktor.client.okhttp)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.material.icons.core)

            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.navigationevent.compose)
            implementation(libs.reorderable)
            implementation(libs.vico.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.ktor.client.core)
        }
        commonMain {
            kotlin.srcDir(generateKinzoConfig)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

sqldelight {
    databases {
        create("KinzoDatabase") {
            packageName.set("com.tomhartill.kinzo.db")
        }
    }
}

compose.resources {
    packageOfResClass = "com.tomhartill.kinzo.resources"
    publicResClass = false
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
