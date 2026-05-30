plugins {
    `maven-publish`
}

val paperApiVersion: String by rootProject.extra
val junitVersion: String by rootProject.extra
val assertjVersion: String by rootProject.extra

description = "Conduit test fixtures — MockEconomy and reusable conformance suites."

dependencies {
    api(project(":conduit-api"))

    // The conformance suites live in src/main so downstream bridges can extend
    // them from their own test source sets; JUnit/AssertJ are therefore api deps.
    api("org.junit.jupiter:junit-jupiter:$junitVersion")
    api("org.assertj:assertj-core:$assertjVersion")

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
            pom {
                name.set("Conduit Test Fixtures")
                description.set(project.description)
                url.set("https://github.com/alazso/conduit")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
