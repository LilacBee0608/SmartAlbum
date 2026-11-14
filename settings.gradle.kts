pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 1. 必须：Google 的官方仓库，包含 AndroidX 等库
        google()
        // 2. 必须：Maven 中央仓库，包含 Coil 等大多数第三方开源库
        mavenCentral()
        // 3. 强烈推荐：为加快速度，添加阿里云的国内镜像
        maven {
            url = uri("https://maven.aliyun.com/repository/google")
        }
        maven {
            url = uri("https://maven.aliyun.com/repository/public")
        }
        // 如果你还有其他自定义仓库，也可以在这里添加
        // maven { url = uri("https://your-private-repo.com/maven") }
    }
}

rootProject.name = "SmartAlbum"
include(":app")
