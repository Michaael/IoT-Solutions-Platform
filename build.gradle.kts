plugins {
    java
    id("org.springframework.boot") version "4.0.7" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.ispf"
    version = findProperty("version")?.toString() ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            name = "ias-releases"
            url = uri("https://maven.mangoautomation.net/repository/ias-release/")
        }
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        maxParallelForks = 1
    }

    dependencies {
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}
