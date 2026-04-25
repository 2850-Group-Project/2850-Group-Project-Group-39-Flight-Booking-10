// group = "com.flightbooking"
// val version = "1.0.0"
val ktorVersion = "2.3.7"
val kotlinVersion = "1.9.22"

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.0"

    // Plugin for KDocs and code quality checks.
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("com.flightbooking.ApplicationKt")
}

dependencies {
    // ktor dependencies
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-pebble:$ktorVersion")
    implementation("io.ktor:ktor-server-sessions:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

    // ktor client dependencies
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")


    // implementation("com.h2database:h2:2.2.224")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    
    // exposed dependencies (abstraction over sql database)
    implementation("org.jetbrains.exposed:exposed-core:0.43.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.43.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.43.0")
    
    
    // library we use for password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // sqlite dependencies
    implementation("org.xerial:sqlite-jdbc:3.41.2.2")

    // simple logging
    implementation("org.slf4j:slf4j-simple:2.0.7")

    // Client-side ContentNegotiation
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-client-json:2.3.7")
    implementation("io.ktor:ktor-client-serialization:2.3.7")
    
    // testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// detekt {
//     buildUponDefaultConfig = true
// }

// tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
//     reports {
//         html.required.set(true)
//         html.outputLocation.set(file("build/reports/detekt/detekt.html"))
//         txt.required.set(true)
//     }
// }