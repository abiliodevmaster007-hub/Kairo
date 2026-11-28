plugins {
    id("java-library")
    id("jacoco")
}

allprojects {
    group = "dev.kairo"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
    }

    dependencies {
        testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
        testImplementation("org.assertj:assertj-core:3.26.3")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport")
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn("test")
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    val hasMainSources = project.file("src/main/java").exists()
    val hasTestSources = project.file("src/test/java").exists()
    val isAndroidRenderer = project.name == "kairo-renderer-android"

    tasks.withType<JacocoCoverageVerification>().configureEach {
        dependsOn("jacocoTestReport")
        onlyIf { hasMainSources && hasTestSources }
        if (isAndroidRenderer) {
            classDirectories.setFrom(
                fileTree(layout.buildDirectory.dir("classes/java/main")) {
                    include("**/KairoSceneEncoder*.class", "**/KairoColorParser*.class")
                }
            )
        }
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = (if (isAndroidRenderer) "0.50" else "0.08").toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn("jacocoTestCoverageVerification")
    }
}

val neutralJavaSubprojects = subprojects.filter {
    it.file("src/main/java").exists() &&
        it.name != "kairo-platform-android" &&
        it.name != "kairo-renderer-android"
}

tasks.named<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates an aggregated JaCoCo coverage report across all neutral JVM modules."

    dependsOn(subprojects.map { it.tasks.named("test") })
    dependsOn(neutralJavaSubprojects.map { it.tasks.named("classes") })

    executionData.setFrom(
        subprojects.map { sub ->
            sub.fileTree(sub.layout.buildDirectory.dir("jacoco")) {
                include("*.exec")
            }
        }
    )
    sourceDirectories.setFrom(
        neutralJavaSubprojects.map { sub ->
            sub.layout.projectDirectory.dir("src/main/java")
        }
    )
    classDirectories.setFrom(
        neutralJavaSubprojects.map { sub ->
            sub.fileTree(sub.layout.buildDirectory.dir("classes/java/main")) {
                exclude("**/android/**")
            }
        }
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Enforces minimum aggregated LINE coverage across all neutral JVM modules."

    dependsOn("jacocoTestReport")

    executionData.setFrom(
        subprojects.map { sub ->
            sub.fileTree(sub.layout.buildDirectory.dir("jacoco")) {
                include("*.exec")
            }
        }
    )
    sourceDirectories.setFrom(
        neutralJavaSubprojects.map { sub ->
            sub.layout.projectDirectory.dir("src/main/java")
        }
    )
    classDirectories.setFrom(
        neutralJavaSubprojects.map { sub ->
            sub.fileTree(sub.layout.buildDirectory.dir("classes/java/main")) {
                exclude("**/android/**")
            }
        }
    )

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
