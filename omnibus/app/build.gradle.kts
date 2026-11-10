import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// The app version. release-please rewrites the version on this line in each release PR
// (see the Releases section of the repo README); do not edit it by hand.
val appVersionName = "0.1.10" // x-release-please-version

// versionCode = major * 10000 + minor * 100 + patch, so it grows with every release.
// minor and patch must stay below 100 or two versions would share a code.
val appVersionCode: Int = run {
    val match = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersionName)) {
        "appVersionName must be X.Y.Z, was '$appVersionName'"
    }
    val (major, minor, patch) = match.destructured.toList().map(String::toInt)
    require(minor in 0..99 && patch in 0..99) {
        "appVersionName '$appVersionName': minor and patch must be 0..99 for versionCode"
    }
    (major * 10000 + minor * 100 + patch).coerceAtLeast(1)
}

// Release signing comes from the environment (CI secrets, or a local keystore). Without all
// four values the release build is left unsigned, so builds and tests need no secrets.
val releaseKeystore = providers.environmentVariable("OMNIBUS_RELEASE_KEYSTORE").orNull
val releaseKeystorePassword = providers.environmentVariable("OMNIBUS_RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("OMNIBUS_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("OMNIBUS_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(releaseKeystore, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.omnibus.omnibus"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.omnibus.omnibus"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // A different application ID lets the debug and release builds be installed side by side.
            // The icon and label are overridden in src/debug/res.
            applicationIdSuffix = ".debug"
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // The screenshot tests resolve themes and resources through the unit-test resource APK.
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()
            }
        }
        // Host-side screenshot tests of composable previews, in src/screenshotTest. References live in
        // src/screenshotTestDebug/reference.
        screenshotTests.create("screenshotTest") {
            engineVersion = libs.versions.screenshot.get()
            // Layoutlib's blur and gradient maths differs from machine to machine in a few pixels of a large
            // render, so a reference recorded on one machine failed on CI at 0.00% difference. This compares
            // structural similarity and tolerates that.
            imageDifferenceThreshold = 0.001f
            targetVariants.add("debug")
            dependencies {
                implementation(libs.androidx.compose.ui.tooling)
                implementation(libs.screenshot.validation.api)
            }
        }
    }
}

androidComponents {
    beforeVariants {
        // The Compose preview engine behind the screenshot tests registers a task for every variant and
        // fails to configure one that has no unit tests.
        (it as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences.core)
    implementation(libs.androidx.datastore.tink) {
        // Pulls in the JVM Tink artifact, whose classes duplicate tink-android's.
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.androidx.material3)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.window)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}