plugins {
    id("com.gradleup.shadow") version "9.4.2"
}

val paperApiVersion: String by rootProject.extra
val junitVersion: String by rootProject.extra
val assertjVersion: String by rootProject.extra
val faststatsVersion: String by rootProject.extra

description = "Conduit runtime — provider registry, dispatch, events, commands, ops."

dependencies {
    api(project(":conduit-api"))

    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // Optional soft dependencies — guarded at runtime via class presence checks.
    compileOnly("me.clip:placeholderapi:2.11.6")
    // FastStats is shaded into the plugin jar (see shadowJar relocation below), so
    // it is always present at runtime without a separate install.
    implementation("dev.faststats.metrics:bukkit:$faststatsVersion")

    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation(project(":conduit-test-fixtures"))
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

// Conduit ships as a single installable plugin jar. Paper isolates plugin
// classloaders, so the API classes the runtime (and dependent bridges) load must
// live inside this jar — bundle conduit-api's compiled output here.
val conduitApiJar = project(":conduit-api").tasks.named<Jar>("jar")

// The shadow jar is the sole deliverable: it bundles conduit-api's classes and
// the shaded FastStats SDK, relocated under our own package to avoid clashing
// with any other plugin that bundles FastStats. Clear the classifier so it
// replaces the plain jar as conduit-core-<version>.jar.
//
// zipTree must be invoked directly in the script body rather than inside a
// provider lambda (e.g. `.map { zipTree(it) }`): the lambda would capture a
// reference to the build-script object, which the configuration cache cannot
// serialize. zipTree itself accepts the lazy archiveFile provider, so the task
// inputs stay correct and up-to-date checks still work.
tasks.shadowJar {
    archiveClassifier.set("")
    dependsOn(conduitApiJar)
    from(zipTree(conduitApiJar.flatMap { it.archiveFile })) {
        // Keep only the API classes; this jar provides its own manifest/LICENSE.
        exclude("META-INF/**")
    }
    relocate("dev.faststats", "so.alaz.conduit.libs.faststats")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Disable the plain jar; shadowJar (with an empty classifier) is the artifact
// installed on servers and consumed by the release workflow.
tasks.named<Jar>("jar") {
    enabled = false
}

// Ensure `assemble`/`build` produce the shadow jar.
tasks.named("assemble") {
    dependsOn(tasks.shadowJar)
}
