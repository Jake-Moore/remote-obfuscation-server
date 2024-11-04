plugins {
    id("java")
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "com.kamikazejam"
version = "1.0.0-SNAPSHOT"

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
    val rosGradlePlugin by plugins.creating {
        id = "com.kamikazejam.ros_gradle_plugin"
        implementationClass = "com.kamikazejam.gradle.plugin.ROSGradlePlugin"
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
                uri("https://repo.luxiouslabs.net/repository/luxious-plugin-snapshots/")
            }else {
                uri("https://repo.luxiouslabs.net/repository/luxious-plugin-releases/")
            }
        }
    }
}
