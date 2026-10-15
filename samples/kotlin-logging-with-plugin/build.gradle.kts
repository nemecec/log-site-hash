plugins {
    alias(libs.plugins.kotlin.jvm)
    application
    alias(libs.plugins.kotlin.logging.compiler.plugin)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "sample.SampleAppKt"
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging-to-logback=true")
}

dependencies {
    implementation(libs.kotlin.logging.jvm)
    implementation(libs.logback.classic)
    implementation(libs.lsh.logback.gelf)

    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("kotlin-logging-to-logback", "true")
}
