dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.eclipse.angus:angus-mail:2.0.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
