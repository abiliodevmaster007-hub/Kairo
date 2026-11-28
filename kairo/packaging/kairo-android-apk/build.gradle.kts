plugins {
    id("com.android.application") version "8.8.0"
}

android {
    namespace = "dev.kairo.android.shell"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "dev.kairo.android.shell"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    externalNativeBuild {
        cmake {
            path = file("../../libraries/kairo-renderer-android/src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("../../examples/login-slice/src/main/res")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("dev.kairo:login-slice:0.1.0-SNAPSHOT")
    implementation("dev.kairo:kairo-platform-android:0.1.0-SNAPSHOT")
    implementation("dev.kairo:kairo-renderer-android:0.1.0-SNAPSHOT")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
}
