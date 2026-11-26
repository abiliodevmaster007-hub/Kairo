dependencies {
    implementation(project(":libraries:kairo-xml"))
    implementation(project(":libraries:kairo-css-parser"))
    implementation(project(":libraries:kairo-runtime"))
    implementation(project(":libraries:kairo-components-basic"))
    implementation(project(":libraries:kairo-components-input"))
    implementation(project(":libraries:kairo-renderer-android"))
    compileOnly("com.google.android:android:4.1.1.4")
}
