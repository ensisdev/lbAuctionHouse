group = "Hooks.Nova"

repositories {
    maven {
        name = "xenondevs"
        url = uri("https://repo.xenondevs.xyz/releases")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("xyz.xenondevs.nova:nova-api:0.18")
}
