pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kairo-android-apk"

includeBuild("../..") {
    dependencySubstitution {
        substitute(module("dev.kairo:kairo-annotations")).using(project(":libraries:kairo-annotations"))
        substitute(module("dev.kairo:kairo-core")).using(project(":libraries:kairo-core"))
        substitute(module("dev.kairo:kairo-ir")).using(project(":libraries:kairo-ir"))
        substitute(module("dev.kairo:kairo-xml")).using(project(":libraries:kairo-xml"))
        substitute(module("dev.kairo:kairo-css-parser")).using(project(":libraries:kairo-css-parser"))
        substitute(module("dev.kairo:kairo-style")).using(project(":libraries:kairo-style"))
        substitute(module("dev.kairo:kairo-state")).using(project(":libraries:kairo-state"))
        substitute(module("dev.kairo:kairo-events")).using(project(":libraries:kairo-events"))
        substitute(module("dev.kairo:kairo-lifecycle")).using(project(":libraries:kairo-lifecycle"))
        substitute(module("dev.kairo:kairo-layout")).using(project(":libraries:kairo-layout"))
        substitute(module("dev.kairo:kairo-components-api")).using(project(":libraries:kairo-components-api"))
        substitute(module("dev.kairo:kairo-components-basic")).using(project(":libraries:kairo-components-basic"))
        substitute(module("dev.kairo:kairo-components-input")).using(project(":libraries:kairo-components-input"))
        substitute(module("dev.kairo:kairo-components-form")).using(project(":libraries:kairo-components-form"))
        substitute(module("dev.kairo:kairo-components-list")).using(project(":libraries:kairo-components-list"))
        substitute(module("dev.kairo:kairo-navigation")).using(project(":libraries:kairo-navigation"))
        substitute(module("dev.kairo:kairo-async")).using(project(":libraries:kairo-async"))
        substitute(module("dev.kairo:kairo-resources")).using(project(":libraries:kairo-resources"))
        substitute(module("dev.kairo:kairo-renderer-api")).using(project(":libraries:kairo-renderer-api"))
        substitute(module("dev.kairo:kairo-runtime")).using(project(":libraries:kairo-runtime"))
        substitute(module("dev.kairo:kairo-renderer-android")).using(project(":libraries:kairo-renderer-android"))
        substitute(module("dev.kairo:kairo-platform-android")).using(project(":libraries:kairo-platform-android"))
        substitute(module("dev.kairo:login-slice")).using(project(":examples:login-slice"))
    }
}
