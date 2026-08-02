group = "Hooks.Denizen"

repositories {
    maven {
        name = "citizensnpcs"
        url = uri("https://maven.citizensnpcs.co/repo")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("com.denizenscript:denizen:1.3.1-SNAPSHOT")
}
