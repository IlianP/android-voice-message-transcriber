import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release-Signierung. Die Werte kommen entweder aus keystore.properties im
// Projektwurzelverzeichnis (lokal, nicht eingecheckt) oder aus Umgebungs-
// variablen (CI). Fehlt beides, faellt der Release-Build auf den Debug-Key
// zurueck, damit ./gradlew assembleRelease auch ohne Keystore durchlaeuft --
// solche APKs lassen sich aber nicht ueber eine echte Installation updaten.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey) ?: System.getenv(envKey)

val releaseStorePath = signingValue("storeFile", "KEYSTORE_FILE")
val hasReleaseKeystore = releaseStorePath != null && file(releaseStorePath).exists()

// versionCode/-Name setzt die CI je Build; lokal bleibt es bei der Dev-Version.
val buildVersionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
val buildVersionName = System.getenv("VERSION_NAME") ?: "1.0-dev"

android {
    namespace = "de.ilianp.audiotranskript"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.ilianp.audiotranskript"
        minSdk = 26
        targetSdk = 35
        versionCode = buildVersionCode
        versionName = buildVersionName
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "Kein Release-Keystore gefunden - Release-Build wird mit dem " +
                        "Debug-Key signiert und ist nicht zum Verteilen geeignet."
                )
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
