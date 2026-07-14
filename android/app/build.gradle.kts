import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun projectLocalProperty(name: String): String? {
    val localPropertiesFile = rootProject.file("local.properties")
    if (!localPropertiesFile.isFile) return null

    return Properties().run {
        localPropertiesFile.inputStream().use(::load)
        getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

fun normalizeApiBaseUrl(rawValue: String): String {
    val value = rawValue.trim()
    if (value.isEmpty()) {
        throw GradleException("API_BASE_URL must not be blank.")
    }

    val normalized = if (value.endsWith("/")) value else "$value/"
    val uri = try {
        URI(normalized)
    } catch (exception: IllegalArgumentException) {
        throw GradleException("API_BASE_URL is malformed: $value", exception)
    }

    if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
        throw GradleException("API_BASE_URL must be an absolute http(s) URL, for example http://10.0.2.2:8000/.")
    }

    return normalized
}

fun String.asBuildConfigString(): String =
    replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

val apiBaseUrl = normalizeApiBaseUrl(
    providers.gradleProperty("API_BASE_URL")
        .orElse(providers.environmentVariable("API_BASE_URL"))
        .orElse(providers.provider { projectLocalProperty("API_BASE_URL") })
        .orElse("http://10.0.2.2:8000/")
        .get()
)
val debugAdminEmail = projectLocalProperty("DEBUG_ADMIN_EMAIL").orEmpty()
val debugAdminPassword = projectLocalProperty("DEBUG_ADMIN_PASSWORD").orEmpty()

logger.lifecycle("Museum API base URL: $apiBaseUrl")

tasks.register("printApiBaseUrl") {
    group = "help"
    description = "Prints the API base URL compiled into the Android app."
    doLast {
        println(apiBaseUrl)
    }
}

android {
    namespace = "com.example.museumapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.museumapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${apiBaseUrl.asBuildConfigString()}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "DEBUG_ADMIN_EMAIL",
                "\"${debugAdminEmail.asBuildConfigString()}\""
            )
            buildConfigField(
                "String",
                "DEBUG_ADMIN_PASSWORD",
                "\"${debugAdminPassword.asBuildConfigString()}\""
            )
        }
        release {
            buildConfigField("String", "DEBUG_ADMIN_EMAIL", "\"\"")
            buildConfigField("String", "DEBUG_ADMIN_PASSWORD", "\"\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
