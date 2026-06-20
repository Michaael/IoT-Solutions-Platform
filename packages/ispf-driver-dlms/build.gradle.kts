dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.gurux:gurux.dlms:4.0.79")
    implementation("org.gurux:gurux.net:1.0.30")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
