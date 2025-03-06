buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.0") // Обновляем AGP
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20") // Синхронизируем с модулем
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}