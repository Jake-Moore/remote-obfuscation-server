plugins {
    id("java")
    id("com.gradle.plugin-publish") version "1.3.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "io.github.jake-moore"
version = "1.0.5"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("com.google.code.gson:gson:2.11.0")

    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
}

gradlePlugin {
    website.set("https://github.com/Jake-Moore/remote-obfuscation-server")
    vcsUrl.set("https://github.com/Jake-Moore/remote-obfuscation-server")
    plugins {
        create("ros_gradle_plugin") {
            id = "io.github.jake-moore.ros-gradle-plugin"
            implementationClass = "io.github.jake_moore.ros_plugin.ROSGradlePlugin"
            displayName = "ROS Gradle Plugin"
            description = "A Gradle plugin to link with Remote Obfuscation Server. (see GitHub)"
            tags = listOf("obfuscation")
        }
    }
}

tasks {
    publish.get().dependsOn(build)
    build.get().dependsOn(shadowJar)
    shadowJar {
        minimize()
        archiveClassifier.set("")
        relocate("com.google", "io.github.jake_moore.ros_gradle_plugin.relocate.google")
        relocate("kotlin", "io.github.jake_moore.ros_gradle_plugin.relocate.kotlin")
        relocate("okhttp3", "io.github.jake_moore.ros_gradle_plugin.relocate.okhttp3")
        relocate("okio", "io.github.jake_moore.ros_gradle_plugin.relocate.okio")
        relocate("org.jetbrains", "io.github.jake_moore.ros_gradle_plugin.relocate.jetbrains")
    }
    javadoc {
        options {
            // Shush
            (this as CoreJavadocOptions).addBooleanOption("Xdoclint:none", true)
        }
    }
}

publishing {
    repositories {
        maven {
            credentials {
                username = System.getenv("LUXIOUS_NEXUS_USER")
                password = System.getenv("LUXIOUS_NEXUS_PASS")
            }
            // Select URL based on version (if it's a snapshot or not)
            url = if (project.version.toString().endsWith("-SNAPSHOT")) {
                uri("https://repo.luxiouslabs.net/repository/maven-snapshots/")
            }else {
                uri("https://repo.luxiouslabs.net/repository/maven-releases/")
            }
        }
    }
}
