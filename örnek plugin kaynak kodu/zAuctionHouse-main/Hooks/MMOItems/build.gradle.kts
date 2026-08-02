group = "Hooks.MMOItems"

repositories {
    maven {
        name = "phoenixdev"
        url = uri("https://nexus.phoenixdevt.fr/repository/maven-public/")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("net.Indyuce:MMOItems-API:6.10-SNAPSHOT")
    compileOnly("io.lumine:MythicLib-dist:1.7.1-SNAPSHOT")
}
