dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("javax.sip:jain-sip-ri:1.3.0-91")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
