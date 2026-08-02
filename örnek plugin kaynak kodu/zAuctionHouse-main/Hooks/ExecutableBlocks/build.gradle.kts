group = "Hooks.ExecutableBlocks"

repositories {
    maven {
        name = "modrinth"
        url = uri("https://api.modrinth.com/maven")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("maven.modrinth:SCore:5.25.6.9")
}
