group = "Hooks.ZelAuction"

dependencies {
    compileOnly(projects.api)
    compileOnly(fileTree("libs") { include("*.jar") })
}
