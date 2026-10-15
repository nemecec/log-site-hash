plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

dependencies {
    api(libs.murmur)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
