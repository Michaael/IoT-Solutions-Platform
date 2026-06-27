plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":packages:ispf-core"))
    implementation(project(":packages:ispf-expression"))
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-plugin-model"))
    implementation(project(":packages:ispf-plugin-workflow"))
    implementation(project(":packages:ispf-ai-api"))
    implementation(project(":packages:ispf-ai-openai-compatible"))
    implementation(project(":packages:ispf-ai-ollama"))

    implementation("io.nats:jnats:2.20.5")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.micrometer:micrometer-registry-otlp")
    implementation("io.micrometer:micrometer-tracing-bridge-otel")

    implementation("com.haulmont.yarg:yarg:2.2.22") {
        exclude(group = "javax.xml.bind", module = "jaxb-api")
    }
    // YARG XlsxFormatter uses docx4j; JDK 11+ removed internal JAXB — required for .xlsx templates
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.9")
    implementation("org.docx4j:docx4j-JAXB-ReferenceImpl:8.3.11")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.mockito:mockito-core")
    testRuntimeOnly("com.h2database:h2")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set("ispf-server")
    from(rootProject.file("LICENSE")) { into("META-INF") }
    from(rootProject.file("NOTICE")) { into("META-INF") }
}

springBoot {
    buildInfo()
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("gradle/driver-packs.json")) {
        into("driver-pack")
    }
}

val driverPacksDir = rootProject.layout.buildDirectory.dir("driver-packs")

tasks.named<Test>("test") {
    dependsOn(rootProject.tasks.named("syncAllDriverPacks"))
    val packsPath = driverPacksDir.get().asFile.absolutePath
    environment("ISPF_DRIVER_PACKS_DIR", packsPath)
    systemProperty("ISPF_DRIVER_PACKS_DIR", packsPath)
    maxHeapSize = "2g"
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    dependsOn(rootProject.tasks.named("syncAllDriverPacks"))
    environment("ISPF_DRIVER_PACKS_DIR", driverPacksDir.get().asFile.absolutePath)
}
