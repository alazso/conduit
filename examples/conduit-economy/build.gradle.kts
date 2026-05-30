val paperApiVersion: String by rootProject.extra

description = "Example Conduit economy provider + consumer plugin for dev-server testing."

dependencies {
    compileOnly(project(":conduit-api"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}

tasks.named<ProcessResources>("processResources") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
