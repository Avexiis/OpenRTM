plugins {
    application
}

dependencies {
    implementation(project(":JJRPC"))
    implementation("com.formdev:flatlaf:3.7.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
    mainClass.set("openrtm.Main")
}

tasks.test {
    useJUnitPlatform()
}
