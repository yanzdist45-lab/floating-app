plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shikuro.reelshort"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shikuro.reelshort"

        minSdk = 26
        targetSdk = 35

        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")
}
