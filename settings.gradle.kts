rootProject.name = "skNetwork"

include("common", "proxy", "spigot")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
        maven("https://repo.skriptlang.org/releases") { name = "skriptlang" }
    }
}
