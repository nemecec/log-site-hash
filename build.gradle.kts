import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.maven.publish)
}

allprojects {
    group = "dev.nemecec.logsitehash"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    // Don't attempt to sign anything if we don't have an in-memory key.
    tasks.withType<Sign>().configureEach {
        enabled = project.findProperty("signingInMemoryKey") != null
    }

    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "testMaven"
                    url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
                }
            }
        }
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            signAllPublications()
            coordinates(group.toString(), "lsh-$name", version.toString())
            pom {
                description.set("Utilities for implementing log site hashing")
                name.set("lsh-${project.name}")
                url.set("https://github.com/nemecec/log-site-hash")
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("nemecec")
                        name.set("Neeme Praks")
                        url.set("https://github.com/nemecec/")
                    }
                }
                scm {
                    url.set("https://github.com/nemecec/log-site-hash")
                    connection.set("scm:git:https://github.com/nemecec/log-site-hash.git")
                    developerConnection.set("scm:git:ssh://git@github.com/nemecec/log-site-hash.git")
                }
            }
        }
    }
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}
