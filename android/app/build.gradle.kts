plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("kapt")
}

android {
    namespace = "com.example.example"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.example"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.github.permissions-dispatcher:permissionsdispatcher:4.8.0")
    androidTestImplementation("org.testng:testng:6.9.6")
    androidTestImplementation("org.testng:testng:6.9.6")
    kapt("com.github.permissions-dispatcher:permissionsdispatcher-processor:4.8.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.insert-koin:koin-android:3.1.2")
    implementation("com.google.android.material:material:1.10.0")
//    implementation("junit:junit:4.13.2")
    implementation("junit:junit:4.13.2") {
        exclude("org.hamcrest", "hamcrest-core")
    }
    implementation("org.hamcrest:hamcrest:2.2")
    implementation("org.mockito:mockito-core:3.12.4")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.osmdroid:osmdroid-android:6.1.6")
}
