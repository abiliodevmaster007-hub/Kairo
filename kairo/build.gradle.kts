plugins {
    id("java-library")
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
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
