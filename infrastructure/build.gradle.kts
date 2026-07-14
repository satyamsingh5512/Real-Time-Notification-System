// Infrastructure module: adapters for DB, Kafka, Redis, FCM, Twilio, SES, WebSocket.
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql:42.7.4")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.0")

    // Notification provider SDKs
    implementation(platform("software.amazon.awssdk:bom:2.28.11"))
    implementation("software.amazon.awssdk:ses")
    implementation("com.twilio.sdk:twilio:10.5.0")
    implementation(platform("com.google.firebase:firebase-admin:9.4.1"))
    implementation("com.google.firebase:firebase-admin:9.4.1")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.springframework.integration:spring-integration-redis:6.3.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
    testImplementation("org.testcontainers:postgresql:1.20.2")
    testImplementation("org.testcontainers:kafka:1.20.2")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
