import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    jacoco
}

allprojects {
    group = "com.uber.notification"
    version = project.findProperty("projectVersion") as String

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "jacoco")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    the<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension>().apply {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:${property("springBootVersion")}")
            // docker-java 3.7+ defaults to Docker API 1.44 (required by current daemons).
            mavenBom("com.github.docker-java:docker-java-bom:3.7.1")
        }
        // Testcontainers 1.21.3 speaks a Docker API version compatible with current
        // daemons; Boot 3.3.4's BOM would otherwise pin it to 1.19.8 (API 1.32 → rejected).
        dependencies {
            dependency("org.testcontainers:testcontainers:1.21.3")
            dependency("org.testcontainers:junit-jupiter:1.21.3")
            dependency("org.testcontainers:postgresql:1.21.3")
            dependency("org.testcontainers:kafka:1.21.3")
            dependency("org.testcontainers:jdbc:1.21.3")
            dependency("org.testcontainers:database-commons:1.21.3")
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        dependsOn(tasks.named("test"))
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    dependencies {
        "compileOnly"("org.projectlombok:lombok:1.18.34")
        "annotationProcessor"("org.projectlombok:lombok:1.18.34")
        "testCompileOnly"("org.projectlombok:lombok:1.18.34")
        "testAnnotationProcessor"("org.projectlombok:lombok:1.18.34")

        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.26.3")
        "testImplementation"("org.mockito:mockito-core:5.13.0")
        "testImplementation"("org.mockito:mockito-junit-jupiter:5.13.0")
    }
}
