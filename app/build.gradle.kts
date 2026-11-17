import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

fun resolveGoogleWebClientId(): String  {
    val envValue = System.getenv("GOOGLE_WEB_CLIENT_ID")?.takeIf {it.isNotBlank()}
    if (envValue != null) {
        println("GOOGLE_WEB_CLIENT_ID is from ENV variable")
        return envValue
    };
    val propertyValue =  project.findProperty("GOOGLE_WEB_CLIENT_ID")?.toString()?.takeIf { it.isNotBlank() }
    if (propertyValue != null) {
        println("GOOGLE_WEB_CLIENT_ID is from gradle.properties")
        return propertyValue
    };
    return "";
}

val releaseStoreFile = signingValue("KASHVAULT_STORE_FILE", "storeFile")?.let { path ->
    val fromRoot = rootProject.file(path)
    val fromModule = file(path)
    when {
        fromRoot.exists() -> fromRoot
        fromModule.exists() -> fromModule
        else -> null
    }
}
val releaseStorePassword = signingValue("KASHVAULT_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("KASHVAULT_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KASHVAULT_KEY_PASSWORD", "keyPassword")
val hasReleaseSigning = releaseStoreFile != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val buildsReleaseArtifact = gradle.startParameter.taskNames.any { taskName ->
    taskName.matches(Regex("(?i)(:app:)?(assemble|bundle|package|install)release"))
}
if (buildsReleaseArtifact && !hasReleaseSigning) {
    throw GradleException(
        "Release signing is required. Configure keystore.properties or all KASHVAULT_* " +
            "environment variables before building a release APK or AAB.",
    )
}

val googleWebClientId = resolveGoogleWebClientId()
if (buildsReleaseArtifact && googleWebClientId.isBlank()) {
    throw GradleException(
        "GOOGLE_WEB_CLIENT_ID is required for release build."
    )
}

android {
    namespace = "com.kashvault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kashvault"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.0"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    val debugKeystore = rootProject.file("debug.keystore")

    signingConfigs {
        if (debugKeystore.exists()) {
            create("debugConfig") {
                storeFile = debugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (debugKeystore.exists()) {
                signingConfig = signingConfigs.getByName("debugConfig")
            }
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release" )
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.google.play.auth)
}
