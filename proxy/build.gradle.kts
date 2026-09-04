dependencies {
    implementation(project(":common"))
    implementation("org.bstats:bstats-bungeecord:${rootProject.property("bstatsVersion")}")
    implementation("org.bstats:bstats-velocity:${rootProject.property("bstatsVersion")}")
    compileOnly("net.md-5:bungeecord-api:${rootProject.property("bungeeApiVersion")}")
    // Velocity brings Configurate and slf4j with it, so the Velocity half needs
    // nothing beyond the API it is compiled against.
    compileOnly("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
}
