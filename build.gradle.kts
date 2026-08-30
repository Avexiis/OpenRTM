plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "openrtm"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
