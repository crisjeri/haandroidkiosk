plugins {
    id("com.android.application")
}

val releaseSigningPropertyNames = listOf(
    "HA_KIOSK_STORE_FILE",
    "HA_KIOSK_STORE_PASSWORD",
    "HA_KIOSK_KEY_ALIAS",
    "HA_KIOSK_KEY_PASSWORD",
)
val releaseSigningProperties = releaseSigningPropertyNames.associateWith {
    providers.gradleProperty(it).orNull
}
val releaseSigningConfigured = releaseSigningProperties.values.all { !it.isNullOrBlank() }

android {
    namespace = "cz.hykel.hakiosk"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.hykel.hakiosk"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(checkNotNull(releaseSigningProperties["HA_KIOSK_STORE_FILE"]))
                storePassword = checkNotNull(releaseSigningProperties["HA_KIOSK_STORE_PASSWORD"])
                keyAlias = checkNotNull(releaseSigningProperties["HA_KIOSK_KEY_ALIAS"])
                keyPassword = checkNotNull(releaseSigningProperties["HA_KIOSK_KEY_PASSWORD"])
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity:1.13.0")

    testImplementation("junit:junit:4.13.2")
}

val verifyReleaseSigningConfiguration = tasks.register("verifyReleaseSigningConfiguration") {
    doLast {
        if (!releaseSigningConfigured) {
            throw GradleException(
                "Release signing is not configured. Define ${releaseSigningPropertyNames.joinToString()} " +
                    "in ~/.gradle/gradle.properties.",
            )
        }

        val storePath = checkNotNull(releaseSigningProperties["HA_KIOSK_STORE_FILE"])
        if (!file(storePath).isFile) {
            throw GradleException("Release keystore does not exist: $storePath")
        }
    }
}

tasks.configureEach {
    if (name == "packageRelease" || name == "signReleaseBundle") {
        dependsOn(verifyReleaseSigningConfiguration)
    }
}
