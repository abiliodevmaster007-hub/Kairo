dependencies {
    api(project(":libraries:kairo-core"))
    api(project(":libraries:kairo-lifecycle"))
    api(project(":libraries:kairo-navigation"))
    api(project(":libraries:kairo-async"))
    api(project(":libraries:kairo-resources"))
    api(project(":libraries:kairo-runtime"))
    api(project(":libraries:kairo-renderer-android"))
    compileOnly("com.google.android:android:4.1.1.4")
}
