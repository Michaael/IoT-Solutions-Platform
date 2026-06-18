plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "iot-solutions-platform-framework"

include(
    "packages:ispf-core",
    "packages:ispf-expression",
    "packages:ispf-driver-api",
    "packages:ispf-server",
    "packages:ispf-driver-mqtt",
    "packages:ispf-driver-modbus",
    "packages:ispf-driver-snmp",
    "packages:ispf-driver-virtual",
    "packages:ispf-plugin-model",
    "packages:ispf-plugin-workflow",
)
