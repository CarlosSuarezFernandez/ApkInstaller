import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from a local, git-ignored keystore.properties file (see README).
// Single source of truth for the version: `appVersionName` in gradle.properties (MAJOR.MINOR.PATCH).
// versionCode is derived as MAJOR * 10000 + MINOR * 100 + PATCH, so it always increases with the name.
val appVersionName: String = providers.gradleProperty("appVersionName").get()
val appVersionCode: Int = appVersionName.split(".").let { parts ->
    require(parts.size == 3 && parts.all { it.toIntOrNull() in 0..99 }) {
        "appVersionName must be MAJOR.MINOR.PATCH with MINOR and PATCH between 0 and 99, was '$appVersionName'"
    }
    parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.carlos.apkinstaller"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.carlos.apkinstaller"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystoreProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

base {
    archivesName.set("ApkInstaller-$appVersionName")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
