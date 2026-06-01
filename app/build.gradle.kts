plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun gitVersionName(): String = try {
    val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0", "--match", "v*")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() != 0 || output.isEmpty()) "0.0.0-dev"
    else output.removePrefix("v")
} catch (e: Exception) {
    "0.0.0-dev"
}

fun gitShortSha(): String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short=7", "HEAD")
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() != 0 || output.isEmpty()) "unknown" else output
} catch (e: Exception) {
    "unknown"
}

fun versionCodeFromName(name: String): Int {
    val parts = name.substringBefore("-").split(".")
    if (parts.size != 3) return 1
    val major = parts[0].toIntOrNull() ?: return 1
    val minor = parts[1].toIntOrNull() ?: return 1
    val patch = parts[2].toIntOrNull() ?: return 1
    return maxOf(1, major * 10000 + minor * 100 + patch)
}

val appVersionName = gitVersionName()
val appVersionCode = versionCodeFromName(appVersionName)

android {
    namespace = "dev.karoorestaurant"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.karoorestaurant"
        minSdk = 23
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"${project.findProperty("supabase.url") ?: ""}\"",
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"${project.findProperty("supabase.anonKey") ?: ""}\"",
        )
        val devTools = (project.findProperty("pitstop.devTools") as? String)?.toBoolean() ?: false
        buildConfigField("boolean", "DEV_TOOLS", devTools.toString())
        // Use resValue, not buildConfigField — `public static final String` constants
        // get inlined into consumer .class files at compile time, so when only the SHA
        // changes between builds the cached MainActivity.class keeps the old folded
        // literal. String resources are read from resources.arsc at runtime instead.
        resValue("string", "git_sha", gitShortSha())
    }

    signingConfigs {
        getByName("debug") {
            val keystore = file("pitstop-debug.jks")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = "android"
                keyAlias = "pitstop"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":data")) {
        exclude(group = "org.xerial", module = "sqlite-jdbc")
    }

    implementation("io.hammerhead:karoo-ext:1.1.8")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("ch.poole:OpeningHoursParser:0.28.2")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
