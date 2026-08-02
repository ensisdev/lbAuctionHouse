group = "Hooks.Nexo"

repositories {
    maven {
        name = "nexo"
        url = uri("https://repo.nexomc.com/releases")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("com.nexomc:nexo:1.21.0")
}
