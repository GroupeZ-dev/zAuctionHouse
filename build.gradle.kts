import org.gradle.api.file.DuplicatesStrategy
import org.gradle.kotlin.dsl.invoke

plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta11"
    id("re.alwyn974.groupez.repository") version "1.0.0"
}

group = "fr.maxlego08.zauctionhouse"
version = "4.0.1.7"

extra.set("targetFolder", file("target/"))
extra.set("apiFolder", file("target-api/"))
extra.set("classifier", System.getProperty("archive.classifier"))
extra.set("sha", System.getProperty("github.sha"))

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "re.alwyn974.groupez.repository")

    group = "fr.maxlego08.zauctionhouse"
    version = rootProject.version

    repositories {
        mavenLocal()
        mavenCentral()

        maven {
            name = "papermc"
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
        maven {
            name = "tcoded-releases"
            url = uri("https://repo.tcoded.com/releases")
        }
        maven {
            name = "tcoded-releases"
            url = uri("https://repo.extendedclip.com/releases/")
        }
        maven {
            name = "faststatsReleases"
            url = uri("https://repo.faststats.dev/releases")
        }
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    tasks.shadowJar {

        archiveBaseName.set("zAuctionHouse")
        archiveAppendix.set(if (project.path == ":") "" else project.name)
        archiveClassifier.set("")
    }

    tasks.compileJava {
        options.encoding = "UTF-8"
        options.release = 21
    }

    tasks.javadoc {
        options.encoding = "UTF-8"
        if (JavaVersion.current().isJava9Compatible)
            (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
        compileOnly("me.clip:placeholderapi:2.11.6")
        compileOnly("fr.maxlego08.menu:zmenu-api:1.1.1.7")

        implementation("fr.maxlego08.sarah:sarah:1.27")
        implementation("com.tcoded:FoliaLib:0.5.1")
        implementation("fr.traqueur.currencies:currenciesapi:1.0.14")

//         testImplementation(platform("org.junit:junit-bom:5.11.4"))
//         testImplementation("org.junit.jupiter:junit-jupiter")
//         testRuntimeOnly("org.junit.platform:junit-platform-launcher")
//         testImplementation("org.mockito:mockito-core:5.14.2")
//         testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
//         testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
//         testImplementation("me.clip:placeholderapi:2.11.6")
//         testImplementation("fr.maxlego08.menu:zmenu-api:1.1.1.7")

//         testRuntimeOnly("org.xerial:sqlite-jdbc:3.47.1.0")
//         testRuntimeOnly("com.mysql:mysql-connector-j:9.1.0")
//         testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    }

//     tasks.withType<Test>().configureEach {
//         useJUnitPlatform()
//         // Mockito (inline mock maker) attache un agent a chaud : on l'autorise explicitement
//         // pour ne pas polluer la sortie avec l'avertissement du JDK 21.
//         jvmArgs("-XX:+EnableDynamicAgentLoading", "-Xshare:off")
//         // Propagation des proprietes zah.test.* (backend SQL, hote, identifiants...).
//         System.getProperties().stringPropertyNames()
//             .filter { it.startsWith("zah.test.") }
//             .forEach { systemProperty(it, System.getProperty(it)) }
//         testLogging {
//             events("failed", "skipped")
//             exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
//             showStandardStreams = false
//         }
//         // Les tests SQL partagent l'etat statique de MigrationManager : un seul fork, en serie.
//         maxParallelForks = 1
//     }

    // Tests temporairement desactives : ni compilation ni execution pendant le build.
    // Pour les reactiver, supprimer ce bloc et decommenter les lignes ci-dessus.
    tasks.matching { it.name in setOf("compileTestJava", "processTestResources", "testClasses", "test") }
        .configureEach { enabled = false }
}

repositories {

}

dependencies {
    api(projects.api)
    api(projects.hooks)
    implementation("dev.faststats.metrics:bukkit:0.29.4")
}

tasks {
    shadowJar {
        relocate("fr.maxlego08.sarah", "fr.maxlego08.zauctionhouse.libs.sarah")
        relocate("com.tcoded.folialib", "fr.maxlego08.zauctionhouse.libs.folialib")
        relocate("fr.traqueur.currencies", "fr.maxlego08.zauctionhouse.libs.currencies")
        relocate("dev.faststats", "fr.maxlego08.zauctionhouse.libs.faststats")

        filesMatching("META-INF/faststats.properties") {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        rootProject.extra.properties["sha"]?.let { sha ->
            archiveClassifier.set("${rootProject.extra.properties["classifier"]}-${sha}")
        } ?: run {
            archiveClassifier.set(rootProject.extra.properties["classifier"] as String?)
        }
        destinationDirectory.set(rootProject.extra["targetFolder"] as File)
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        from("resources")
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
