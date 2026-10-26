import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Firebase is opt-in: it is enabled only when google-services.json is present.
// This keeps debug builds and fresh clones working without a Firebase project.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

// Snapdragon GPU/NPU build is opt-in: -Psnapdragon selects the accelerated
// arm64 libraries. The default build uses the official runtime-dispatch CPU set.
val useSnapdragon = project.hasProperty("snapdragon")
val llamaJniDir = "src/" + (if (useSnapdragon) "snapdragon" else "standard") + "/jniLibs"

// Release signing is opt-in via android/keystore.properties, which is gitignored.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile")?.let {
    rootProject.file(it).exists()
} == true

android {
    namespace = "com.agentdeck.app"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "com.agentdeck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("en")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (useSnapdragon) versionNameSuffix = "-snapdragon"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                arguments += "-DLLAMA_LIBS_BASE=$projectDir/$llamaJniDir"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        jniLibs {
            // Backend variants are dlopen'd by ggml from the native library dir.
            useLegacyPackaging = true
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir(llamaJniDir)
            kotlin.srcDir("build/generated/ksp/main/kotlin")
            kotlin.srcDir("build/generated/ksp/main/java")
        }
        getByName("debug") {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
            kotlin.srcDir("build/generated/ksp/debug/java")
        }
    }
}

// AGP 9 has no applicationVariants API and prunes stale files from outputs/apk,
// so copy each finished APK to the project dist/ folder with the native-set
// name. Standard and snapdragon builds then never overwrite each other.
val nativeSuffix = if (useSnapdragon) "snapdragon" else "standard"
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    doLast {
        val buildType = name.removePrefix("assemble").lowercase()
        val outDir = layout.buildDirectory.dir("outputs/apk/$buildType").get().asFile
        val distDir = rootProject.projectDir.parentFile.resolve("dist")
        distDir.mkdirs()
        outDir.listFiles { file -> file.extension == "apk" }?.forEach { apk ->
            val target =
                if (buildType == "release") "agentdeck-$nativeSuffix-1.0.0.apk"
                else "agentdeck-$nativeSuffix-debug.apk"
            apk.copyTo(distDir.resolve(target), overwrite = true)
        }
    }
}

ksp {
    // Keep generated Room schemas in version control so future changes can
    // ship explicit migrations instead of wiping saved tasks.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    // Pure-Java git for the on-device agent workspace (stock Android has no git binary).
    // SSH client for the Termux bridge (real Node/Python/Gradle on-device).
    implementation("com.github.mwiede:jsch:0.2.17")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.5.202508271544-r")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    // OkHttp 5: Happy Eyeballs IPv4/IPv6 fallback fixes connection hangs on flaky networks.
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    
    // Firebase dependencies
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
}
