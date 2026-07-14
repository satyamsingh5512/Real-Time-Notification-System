// Application module: use cases / orchestration. Depends only on domain + common.
dependencies {
    implementation(project(":common"))
    implementation(project(":domain"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("com.fasterxml.jackson.core:jackson-databind")
}
