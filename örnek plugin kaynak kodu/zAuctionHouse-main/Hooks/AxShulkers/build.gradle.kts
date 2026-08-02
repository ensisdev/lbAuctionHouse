group = "Hooks.AxShulkers"

repositories {
    maven {
        name = "artillex-studios"
        url = uri("https://repo.artillex-studios.com/releases/")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("com.artillexstudios:AxShulkers:1.22.3") {
        isTransitive = false
    }
}
