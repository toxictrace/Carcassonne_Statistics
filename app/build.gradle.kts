plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.0.20" // Обновляем до 2.0.20, так как 2.1.0 пока нестабильна
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp") version "2.0.20-1.0.24" // Совместимая версия KSP
}

android {
    namespace = "by.toxic.carstat"
    compileSdk = 34

    defaultConfig {
        applicationId = "by.toxic.carstat"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        dataBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1") // Обновляем до последней версии
    implementation("androidx.appcompat:appcompat:1.7.0") // Обновляем
    implementation("com.google.android.material:material:1.12.0") // Обновляем
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.3") // Обновляем
    implementation("androidx.navigation:navigation-ui-ktx:2.8.3") // Обновляем
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.20") // Синхронизируем с плагином
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.0.20") // Синхронизируем
    implementation("androidx.room:room-runtime:2.6.1") // Обновляем
    implementation("androidx.room:room-ktx:2.6.1") // Обновляем
    ksp("androidx.room:room-compiler:2.6.1") // Обновляем
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6") // Обновляем
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6") // Обновляем
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1") // Обновляем до последней стабильной
}