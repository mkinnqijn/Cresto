import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val devCompileSdkOverride = providers.gradleProperty("devCompileSdk").orNull?.toIntOrNull()

android {
    namespace = "com.nevoit.glasensedemo"
    compileSdk {
        // 官方配置是 37；API 37 的 SDK platform 尚未公开发布时可以 -PdevCompileSdk=36 构建（仅本地/CI 变通）
        version = release(devCompileSdkOverride ?: 37)
    }

    defaultConfig {
        applicationId = "com.nevoit.glasensedemo"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

configurations.configureEach {
    exclude(group = "androidx.compose.material3")
}

dependencies {
    implementation(project(":glasense-ui"))
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
