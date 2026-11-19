import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// local.properties から API_URL / DEVICE_ID を読み込む（ファイルがなければ空文字でフォールバック）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.example.walkdetection_20260818"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.walkdetection_20260818"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val apiUrl = localProps.getProperty("API_URL") ?: ""
        val deviceId = localProps.getProperty("DEVICE_ID") ?: "pixel-7a-test"
        val apiKey = localProps.getProperty("API_KEY") ?: ""
        if (apiUrl.isEmpty()) {
            println("WARNING: API_URL is not set in local.properties. API calls will fail.")
        }
        if (apiKey.isEmpty()) {
            println("WARNING: API_KEY is not set in local.properties. POST /events will return 403.")
        }
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        buildConfigField("String", "DEVICE_ID", "\"$deviceId\"")
        buildConfigField("String", "API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
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
        buildConfig = true
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}