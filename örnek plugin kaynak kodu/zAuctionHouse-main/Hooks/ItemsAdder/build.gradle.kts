group = "Hooks.ItemsAdder"

repositories {
    maven {
        name = "itemsadder"
        url = uri("https://maven.devs.beer/")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("dev.lone:api-itemsadder:4.0.10")
}
