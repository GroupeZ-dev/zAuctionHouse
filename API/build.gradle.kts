plugins {
    id("re.alwyn974.groupez.publish") version "1.0.0"
}

rootProject.extra.properties["sha"]?.let { sha ->
    version = sha
}

tasks {
    shadowJar {


        destinationDirectory.set(rootProject.extra["apiFolder"] as File)
    }

    build {
        dependsOn(shadowJar)
    }
}

publishConfig {
    githubOwner.set("GroupeZ-dev")
}
