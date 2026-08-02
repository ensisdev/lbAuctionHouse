group = "Hooks.Oraxen"

repositories {
    maven {
        name = "oraxen"
        url = uri("https://repo.oraxen.com/releases")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("io.th0rgal:oraxen:1.171.0")
}
