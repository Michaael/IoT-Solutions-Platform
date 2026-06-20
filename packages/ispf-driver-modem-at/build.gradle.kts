dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("com.fazecast:jSerialComm:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
