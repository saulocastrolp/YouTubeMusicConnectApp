plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

buildscript {
    dependencies {
        classpath("com.google.gms:google-services:4.3.15") // versão atual
    }
}

android {
    namespace = "com.saulocastrodev.youtubemusicconnect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.saulocastrodev.youtubemusicconnect"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
        }
    }
}

dependencies {
    // Jetpack Compose atualizado
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Navegação no Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Retrofit para chamadas API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Gson para JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // OkHttp para WebSocket e HTTP
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Coil para carregamento de imagens
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Accompanist para permissões
    implementation("com.google.accompanist:accompanist-permissions:0.33.0-alpha")
    implementation("io.coil-kt:coil-compose:2.4.0")


    // Testes
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.0")

    implementation("com.google.android.material:material:1.11.0")

    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation("io.socket:socket.io-client:2.1.0") {
        exclude(group = "org.json", module = "json")
    }

    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation("com.google.accompanist:accompanist-swiperefresh:0.30.1")

}
