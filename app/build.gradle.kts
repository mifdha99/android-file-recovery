plugins {
    // AGP 9 provides built-in Kotlin support — no org.jetbrains.kotlin.android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.eslam.recovery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.eslam.recovery"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // R8 + resource shrinking: far smaller APK and much faster Compose than a debug build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Sideload-only project: release is signed with the local debug key so `assembleRelease`
            // yields an installable APK without any secrets. Replace for a store release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// AGP 9.2.1 bundles the Kotlin 2.2.10 compiler, but some latest libraries transitively pull a newer
// kotlin-stdlib whose metadata that compiler cannot read — align the runtime to the compiler version.
configurations.configureEach {
    resolutionStrategy.force(
        "org.jetbrains.kotlin:kotlin-stdlib:2.2.10",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.2.10",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.10",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
