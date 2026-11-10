import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load signing secrets from local.properties (never committed to git)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

/**
 * Play Console → Monetize with Play → Monetization setup → Licensing: the app's public RSA key.
 * It is public (not a secret), but lives in local.properties beside the signing config so every
 * build machine supplies it deliberately. PurchaseVerifier checks each purchase signature with it.
 */
val playLicenseKey = (localProps["PLAY_LICENSE_KEY"] as? String)?.trim().orEmpty()

// A release build without the key would reject every purchase (PurchaseVerifier fails closed), so
// it fails here instead of shipping. Hooked to preReleaseBuild, which every route into the release
// variant runs (bundleRelease, assembleRelease, plain `assemble`/`build`, the Studio wizard) —
// matching on typed task names missed `assemble`. Debug builds may omit the key.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (playLicenseKey.isEmpty()) {
            throw GradleException(
                "PLAY_LICENSE_KEY is missing from local.properties. Copy it from Play Console → " +
                    "Monetize with Play → Monetization setup → Licensing."
            )
        }
    }
}

android {
    namespace = "com.vi5hnu.nightshield"
    compileSdk = 36

    signingConfigs {
        create("release") {
            val keystorePath = localProps["KEYSTORE_PATH"] as? String
            val storePass = localProps["STORE_PASSWORD"] as? String
            val alias = localProps["KEY_ALIAS"] as? String
            val keyPass = localProps["KEY_PASSWORD"] as? String
            if (keystorePath != null && storePass != null && alias != null && keyPass != null) {
                storeFile = file(keystorePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    defaultConfig {
        applicationId = "com.vi5hnu.nightshield"
        minSdk = 29
        targetSdk = 36
        versionCode = 14
        versionName = "3.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenseKey\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false

        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        // Lets the About row read the real version name instead of a hardcoded string.
        buildConfig = true
    }

    lint {
        // False positive: fires when registerForActivityResult is used, but this app
        // extends ComponentActivity (not FragmentActivity), so Fragment version is irrelevant.
        disable += "InvalidFragmentVersionForActivityResult"
    }
}

// Kotlin 2.x compilerOptions DSL (replaces the removed kotlinOptions block).
kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Color picker
    implementation(libs.colorpicker.compose)

    // Window manager for overlay support
    implementation(libs.androidx.window)

    // WorkManager (retained for background compatibility)
    implementation(libs.androidx.work.runtime.ktx)

    // AdMob
    implementation(libs.play.services.ads)
    // UMP: GDPR/UK consent before ads are requested (AdConsentManager)
    implementation(libs.user.messaging.platform)

    // Google Play Billing
    implementation(libs.billing.ktx)

    // In-app review
    implementation(libs.play.review.ktx)
}
