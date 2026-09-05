plugins {
    `java-library`
}

base {
    archivesName.set("JJRPC")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveFileName.set("JJRPC.jar")
}
