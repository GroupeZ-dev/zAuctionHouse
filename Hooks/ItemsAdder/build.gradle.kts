group = "Hooks.ItemsAdder"

repositories {
    maven {
        name = "itemsadder"
        url = uri("https://maven.devs.beer/")
    }
}

dependencies {
    compileOnly(projects.api)
    compileOnly("beer.devs:itemsadder-api:4.0.17")
}
