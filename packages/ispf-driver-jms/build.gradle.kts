dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.apache.activemq:activemq-client:6.1.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
