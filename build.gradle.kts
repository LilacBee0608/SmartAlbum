plugins {
    id("com.android.application") version "9.0.0" apply false
    id("com.google.devtools.ksp") version "2.3.5" apply false
    // 与 AGP 9 内置 Kotlin 2.2.10 一致；启用 Compose 时 Kotlin 2.0+ 要求此插件
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}