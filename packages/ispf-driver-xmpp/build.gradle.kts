dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.igniterealtime.smack:smack-tcp:4.4.8")
    implementation("org.igniterealtime.smack:smack-im:4.4.8")
    implementation("org.igniterealtime.smack:smack-extensions:4.4.8")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
