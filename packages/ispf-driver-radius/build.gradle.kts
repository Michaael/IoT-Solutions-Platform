dependencies {
    implementation(project(":packages:ispf-driver-api"))
    implementation(project(":packages:ispf-core"))

    implementation("org.tinyradius:tinyradius:1.1.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}
