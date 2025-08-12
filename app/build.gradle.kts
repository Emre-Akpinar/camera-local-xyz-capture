plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.test2"   // <-- your package name
    compileSdk = 34
    kotlinOptions {
        jvmTarget = "17"
    }
    defaultConfig {
        applicationId = "com.example.test2"  // keep consistent
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    kotlin {
        jvmToolchain(17) // Ensure you have JDK 17 installed
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {

    androidTestImplementation ("junit:junit:4.13.2") // Or the latest version
    // For AndroidX Test libraries (which you are using based on the import)
    androidTestImplementation ("androidx.test.ext:junit:1.1.5") // Or the latest version
    androidTestImplementation ("androidx.test.espresso:espresso-core:3.5.1") // Or the latest version

    testImplementation("junit:junit:4.13.2")
    implementation("com.google.ar:core:1.45.0")      // ARCore (or latest)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
