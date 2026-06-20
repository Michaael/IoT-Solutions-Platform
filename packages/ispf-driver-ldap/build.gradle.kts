dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.unboundid:unboundid-ldapsdk:7.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
