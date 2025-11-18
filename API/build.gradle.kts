plugins {
    id("re.alwyn974.groupez.publish") version "1.0.0"
}

rootProject.extra.properties["sha"]?.let { sha ->
    version = sha
}

dependencies {
    implementation("fr.maxlego08.sarah:sarah:1.20")
    implementation("com.tcoded:FoliaLib:0.5.1")
    implementation("fr.traqueur.currencies:currenciesapi:1.0.10")
}

tasks {
    shadowJar {

        relocate("fr.maxlego08.sarah", "fr.maxlego08.zauctionhouse.libs.sarah")
        relocate("com.tcoded.folialib", "fr.maxlego08.zauctionhouse.libs.folialib")
        relocate("fr.traqueur.currencies", "fr.maxlego08.zauctionhouse.libs.currencies")

        destinationDirectory.set(rootProject.extra["apiFolder"] as File)
    }

    build {
        dependsOn(shadowJar)
    }
}

publishConfig {
    githubOwner.set("GroupeZ-dev")
}
