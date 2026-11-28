plugins {
    id("application")
}

application {
    mainClass.set("dev.kairo.cli.KairoCli")
}

dependencies {
    implementation(project(":libraries:kairo-core"))
    implementation(project(":libraries:kairo-ir"))
    implementation(project(":libraries:kairo-xml"))
    implementation(project(":libraries:kairo-css-parser"))
    implementation(project(":libraries:kairo-components-api"))
    implementation(project(":libraries:kairo-components-basic"))
    implementation(project(":libraries:kairo-components-input"))
    implementation(project(":libraries:kairo-components-form"))
    implementation(project(":libraries:kairo-components-list"))
    implementation(project(":libraries:kairo-navigation"))
    implementation(project(":libraries:kairo-inspector"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
