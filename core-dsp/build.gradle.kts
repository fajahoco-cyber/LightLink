plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core-protocol"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
