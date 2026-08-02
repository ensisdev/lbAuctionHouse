group = "Hooks.Slimefun"

repositories {
    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("com.github.Slimefun:Slimefun4:RC-37")
}
