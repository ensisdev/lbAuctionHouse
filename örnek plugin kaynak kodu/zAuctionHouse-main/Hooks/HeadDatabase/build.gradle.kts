group = "Hooks.HeadDatabase"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(projects.api)
    compileOnly("com.arcaniax:HeadDatabase-API:1.3.2")
}
