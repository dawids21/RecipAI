import java.io.FileInputStream
import java.util.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // START: FlutterFire Configuration
    id("com.google.gms.google-services")
    // END: FlutterFire Configuration
    // The Flutter Gradle Plugin must be applied after the Android Gradle Plugin.
    // Kotlin is not applied here: see the built-in Kotlin migration in gradle.properties.
    id("dev.flutter.flutter-gradle-plugin")
}

val uploadKeyProperties = Properties()
val uploadKeyPropertiesFile = rootProject.file("upload-key.properties")
if (uploadKeyPropertiesFile.exists()) {
    uploadKeyProperties.load(FileInputStream(uploadKeyPropertiesFile))
}
val debugKeystoreFile = rootProject.file("debug_keystore.jks")

android {
    namespace = "xyz.stasiak.recipai"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "28.2.13676358"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "xyz.stasiak.recipai"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        getByName("debug") {
            storeFile = debugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            if (uploadKeyPropertiesFile.exists()) {
                keyAlias = uploadKeyProperties["keyAlias"] as String
                keyPassword = uploadKeyProperties["keyPassword"] as String
                storeFile = uploadKeyProperties["storeFile"]?.let { file(it) }
                storePassword = uploadKeyProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
        }
    }
}

gradle.taskGraph.whenReady {
    val wantsDebugSigning = allTasks.any {
        it.name.contains("Debug") || it.name.contains("Profile")
    }
    val wantsRelease = allTasks.any { it.name.contains("Release") }

    if (wantsDebugSigning && !debugKeystoreFile.exists()) {
        throw GradleException("Shared debug keystore missing. Run ./recipai.sh setup")
    }
    if (wantsRelease && !uploadKeyPropertiesFile.exists()) {
        throw GradleException("Upload signing not configured. Build with ./recipai.sh build-mobile")
    }
}

flutter {
    source = "../.."
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    // Provides androidx.core.content.FileProvider, used to share the log file
    // via ACTION_SEND in ShareIntentBridge.
    implementation("androidx.core:core-ktx:1.13.1")
}
