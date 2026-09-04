dependencies {
    implementation(project(":common"))
    implementation("org.bstats:bstats-bukkit:${rootProject.property("bstatsVersion")}")
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly("com.github.SkriptLang:Skript:${rootProject.property("skriptVersion")}")
}
