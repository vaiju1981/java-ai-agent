// Root build — shared configuration for every module.
// Each module declares its own `plugins {}` (java-library, or application for examples);
// here we only set things common to all of them.

allprojects {
    group = "io.github.vaiju1981"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // Target Java 21 (the baseline consumers need) even though we build on a newer JDK.
    // `configureEach` is lazy, so this applies once a module adds the java/application plugin.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        // Keep parameter names at runtime — useful for tool/skill reflection later.
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
