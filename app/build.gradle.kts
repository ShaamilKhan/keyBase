plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.keybase.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.keybase.app"
        minSdk = 31 // Android 12 floor; Android 16 is the primary target
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    // Release signing comes from CI secrets; without them the APK is debug-signed.
    val ksFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ksFile != null && file(ksFile).exists()) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    testOptions {
        // android.* calls return defaults instead of throwing, so pure-logic classes load in plain JVM tests.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/**", "META-INF/*.version")
    }
}

// Argon2 at the real 128 MiB setting runs inside several tests.
tasks.withType<Test>().configureEach { maxHeapSize = "1g" }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.8")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Argon2id key derivation
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    // Pattern-aware password entropy estimate (MIT, pure Java, same library KeePassDX uses)
    implementation("me.gosimple:nbvcxz:1.5.1")

    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is a stub in unit tests; the real one lets the vault serialiser run.
    testImplementation("org.json:json:20240303")
}
