// 示例：把 DEX 插件编成 classes.dex 再塞进 zip。
// 放进 Loom 仓库里可以直接用 :plugin-api；单独建工程时把 plugin-api 的源码拷进来即可。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.loom.example"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    compileOnly(project(":plugin-api"))   // 宿主运行时提供，不进 dex
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    compileOnly("androidx.compose.ui:ui")
    compileOnly("androidx.compose.material3:material3")
}
