dependencies {
    api(project(":libraries:kairo-renderer-api"))
    api(project(":libraries:kairo-state"))
    api(project(":libraries:kairo-components-input"))
    api(project(":libraries:kairo-resources"))
    // Android SDK compileOnly dependency isolated strictly inside renderer-android and platform-android
    compileOnly("com.google.android:android:4.1.1.4")
}
