import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val osName = System.getProperty("os.name").lowercase()
val osArchitecture = System.getProperty("os.arch").lowercase()
val nativeClassifier = when {
    osName.contains("linux") && (osArchitecture == "amd64" || osArchitecture == "x86_64") -> "linux-x86_64"
    osName.contains("linux") && (osArchitecture == "aarch64" || osArchitecture == "arm64") -> "linux-arm64"
    osName.contains("windows") && (osArchitecture == "amd64" || osArchitecture == "x86_64") -> "windows-x86_64"
    osName.contains("mac") && (osArchitecture == "amd64" || osArchitecture == "x86_64") -> "macosx-x86_64"
    osName.contains("mac") && (osArchitecture == "aarch64" || osArchitecture == "arm64") -> "macosx-arm64"
    else -> throw GradleException("Video capture is not packaged for $osName $osArchitecture")
}

dependencies {
    implementation(project(":JJRPC"))
    implementation("com.formdev:flatlaf:3.7.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.bytedeco:javacv:1.5.13")
    runtimeOnly("org.bytedeco:javacpp:1.5.13:$nativeClassifier")
    runtimeOnly("org.bytedeco:ffmpeg:8.0.1-1.5.13:$nativeClassifier")
    runtimeOnly("org.bytedeco:openblas:0.3.31-1.5.13:$nativeClassifier")
    runtimeOnly("org.bytedeco:opencv:4.13.0-1.5.13:$nativeClassifier")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

application {
    mainClass.set("openrtm.Main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    archiveBaseName.set("OpenRTM")
    archiveVersion.set("")
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

tasks.named("assemble") {
    dependsOn(tasks.named("shadowJar"))
}
