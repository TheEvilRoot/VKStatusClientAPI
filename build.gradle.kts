import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.21"
    `maven-publish`
}

group = "VKStatusClientAPI"
version = "1.4.2"

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(sourceSets.getByName("main").allSource)
}
artifacts.add("archives", sourcesJar)

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.theevilroot.vkstatus"
            artifactId = "client-api"
            version = version
            from(components["java"])
            artifact(sourcesJar)
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.socket:socket.io-client:1.0.0")
    implementation("io.reactivex.rxjava2:rxjava:2.2.7")
    implementation("com.google.code.gson:gson:2.8.5")

}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}