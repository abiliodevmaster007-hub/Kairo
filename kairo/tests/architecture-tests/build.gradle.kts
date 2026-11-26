dependencies {
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation(project(":libraries:kairo-core"))
    testImplementation(project(":libraries:kairo-runtime"))
    testImplementation(project(":libraries:kairo-components-basic"))
    testImplementation(project(":libraries:kairo-components-input"))
    testImplementation(project(":libraries:kairo-components-form"))
    testImplementation(project(":libraries:kairo-components-list"))
    testImplementation(project(":libraries:kairo-navigation"))
    testImplementation(project(":libraries:kairo-async"))
    testImplementation(project(":libraries:kairo-resources"))
}
