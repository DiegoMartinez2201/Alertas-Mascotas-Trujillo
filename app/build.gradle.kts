plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // plugin de google services para procesar google-services.json
    id("com.google.gms.google-services")
}

android {
    namespace = "com.primero.alertamascota"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.primero.alertamascota"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Glide para cargar imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // Material Design
    implementation("com.google.android.material:material:1.11.0")
    // CircleImageView para foto de perfil circular
    implementation("de.hdodenhof:circleimageview:3.1.0")
    // DrawerLayout
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    // CardView (necesario para el formulario)
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    // Google Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")

    // Google Location Services (para ubicación en tiempo real)
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Para selección de imágenes (Activity Result API)
    implementation("androidx.activity:activity-ktx:1.8.2")

    // Firebase BoM (gestiona versiones automáticamente)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // Firebase Auth + Firestore + Storage (las versiones las maneja el BoM)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")

    // Glide para cargar imágenes
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.zxing:core:3.5.2")

    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")


    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}