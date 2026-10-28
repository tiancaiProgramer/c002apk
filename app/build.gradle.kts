import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.properties.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.dagger.hilt.android)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.google.protobuf)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.plugin.compose)
    alias(libs.plugins.kotlin.parcelize)
}

val gitCommitCount = if (rootProject.file(".git").exists()) providers.exec {
    commandLine("git", "rev-list", "HEAD", "--count")
}.standardOutput.asText.map { it.trim().toInt() }.get() else 248
val gitCommitHash = if (rootProject.file(".git").exists()) providers.exec {
    commandLine("git", "rev-parse", "--verify", "--short", "HEAD")
}.standardOutput.asText.map { it.trim() }.get() else "source-archive"

// A local, signed release target for editor instrumentation. Never enable its
// shell-only launch entry on the normal application ID.
val articlePerformanceTest = providers.gradleProperty("articlePerformanceTest").orNull == "true"
if (articlePerformanceTest) check(providers.gradleProperty("testApplicationId").orNull ==
    "com.example.c001apk.replyfix.articletest")

android {
    namespace = "com.example.c001apk.compose"
    compileSdk = 35
    buildToolsVersion = "36.0.0"
    if (articlePerformanceTest) {
        testBuildType = "release"
        sourceSets.getByName("release").manifest.srcFile("src/debug/AndroidManifest.xml")
    }

    defaultConfig {
        applicationId = providers.gradleProperty("testApplicationId")
            .orElse("com.example.c001apk.replyfix").get()
        manifestPlaceholders["applicationLabel"] =
            if (providers.gradleProperty("testApplicationId").isPresent) "c002apk 独立测试"
            else "@string/app_name"
        minSdk = 24
        targetSdk = 35
        // The app has one default Chinese string set. Keep Chinese variants and the
        // libraries' English fallback instead of packaging their unused translations.
        resourceConfigurations += listOf("zh", "en")
        versionCode = providers.gradleProperty("releaseVersionCode").orElse(gitCommitCount.toString()).get().toInt()
        versionName = providers.gradleProperty("releaseVersionName").orElse(gitCommitHash).get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    val localProperties = Properties().also {
        val properties = rootProject.file("local.properties")
        if (properties.exists())
            it.load(properties.inputStream())
    }

    val config = localProperties.getProperty("KEYSTORE_PATH")?.let {
        signingConfigs.create("release") {
            storeFile = file(it)
            storePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
            keyAlias = localProperties.getProperty("KEY_ALIAS")
            keyPassword = localProperties.getProperty("KEY_PASSWORD")
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    val disableR8 = (project.findProperty("disableR8") as? String) == "true"

    buildTypes {
        all {
            signingConfig = config ?: signingConfigs["debug"]
        }
        release {
            isMinifyEnabled = !disableR8
            isShrinkResources = !disableR8
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (articlePerformanceTest) proguardFile("article-performance-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packagingOptions.resources.excludes += setOf(
        "META-INF/**",
        "okhttp3/**",
        "kotlin/**",
        "org/**",
        "**.properties",
        "**.bin",
        "**/*.proto"
    )

    dependenciesInfo.includeInApk = false

    applicationVariants.configureEach {
        outputs.configureEach {
            if (baseName == "release")
                (this as? ApkVariantOutputImpl)?.outputFileName =
                    "c002apk_$versionName($versionCode).apk"
        }
    }
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

protobuf {
    protoc {
        artifact = libs.google.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                register("java") {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    if (articlePerformanceTest) implementation("androidx.tracing:tracing:1.1.0")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Espresso requires forceEnableAppTracing(), absent from the app's transitive 1.0.0.
    debugImplementation("androidx.tracing:tracing:1.1.0")
    testImplementation(libs.junit)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewModel.compose)
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.5")
    implementation("androidx.savedstate:savedstate:1.2.1")
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.android)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.material3.adaptive.navigation.suite)

    implementation(libs.google.accompanist.drawablepainter)
    implementation(libs.google.android.material)
    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.android.compiler)
    implementation(libs.google.protobuf.kotlin.lite)

    implementation(libs.squareup.okhttp3.logging.interceptor)
    implementation(libs.squareup.retrofit)
    implementation(libs.squareup.retrofit.converter.gson)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.jp.wasabeef.transformers.coil)
    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.jbcrypt)
    implementation(libs.jsoup)
    implementation(libs.toolbar.compose)
    implementation(libs.oss.android.sdk)
    implementation(libs.material.kolor)

    implementation(project(":mojito"))
    implementation(project(":SketchImageViewLoader"))
    implementation(project(":media-support"))
    implementation("com.github.MikaelZero.mojito:coilimageloader:1.8.7") {
        exclude(group = "com.github.MikaelZero.mojito", module = "mojito")
    }
}
