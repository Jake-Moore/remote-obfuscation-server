## Gradle Plugin Purpose
The gradle plugin for this project aims to assist in your obfuscation needs, including:
- automated obfuscation for your build
- automated watermarking of obfuscated jars
  - including a gradle task for extracting watermark
- automated stacktrace restoration from obfuscated state
  - using a CLI-like gradle task

## Adding the Plugin
The ROS Gradle Plugin is published to [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.jake-moore.ros-gradle-plugin) under the id `io.github.jake-moore.ros-gradle-plugin`  

#### Kotlin DSL
```kotlin
plugins {
  id("io.github.jake-moore.ros-gradle-plugin") version "{VERSION}"
}
```

#### Groovy DSL
```groovy
plugins {
  id "io.github.jake-moore.ros-gradle-plugin" version "{VERSION}"
}
```

#### Version
Please check the [official gradle plugin page](https://plugins.gradle.org/plugin/io.github.jake-moore.ros-gradle-plugin) for the latest version to use in place of {VERSION}.

## Configuring the Plugin
The ROS Gradle Plugin requires some setup before its tasks can be used. Tasks will quit with errors unless ROS is properly configured.  
Add the following to your build file (this example assumes kotlin DSL)
```kotlin
// Configure ROS
extensions.configure<io.github.jake_moore.ros_plugin.ROSGradleConfig>("rosConfig") {
  // The API URL should be the BASE url that the ROS backend is deployed to.
  // You can verify if this is the correct URL by going to one of the endpoints, like '${apiUrl}/api/obfuscate' and verifying the GET request responded with a ready message.
  apiUrl = "https://obf.luxiouslabs.net/"
  configFilePath = project.file("allatori.xml").absolutePath

  // This field is `false` by default.
  // When set to true, the `rosObfuscateJar` task won't delete the `jar` and `shadowJar` outputs
  // This default helps ensure only the obfuscated jar remains in the build folder, reducing the chance
  //  of someone taking the wrong jar from the builds folder
  // keepOriginalJar = true
  
  // This field is set to 60 by default.
  // This controls how many times we attempt to check the state of an obfuscation job
  // The `pollIntervalMs` controls the time we wait between each check
  pollMaxAttempts = 20
  
  // This field is set to 5000 by default. (5 seconds)
  // This controls the time we wait between each check
  // The `pollMaxAttempts` controls the max number of checks
  pollIntervalMs = 1000
}

// For your application you may want to have obfuscation run for every build
tasks.build.get().dependsOn(tasks.named("rosObfuscateJar"))
```




**REMINDER:** In order to run any of the ROS tasks, you will need to configured your environment variable so requests can be authenticated. See the [User-Authorization](https://github.com/Jake-Moore/remote-obfuscation-server/blob/main/.docs/User-Authorization.md) guide for details.

### Integration with Gradle Shadow Plugin
By default, the obfuscation task (`rosObfuscateJar`) will consume the output of the gradle `jar` task. However, in the presense of a task called `shadowJar` (as implemented by the Shadow plugin), `rosObfuscateJar` **will instead consume the `shadowJar` output file for obfuscation.**  
Additionally, when shadowJar is detected, the `keepOriginalJar` feature will delete BOTH the `jar` and `shadowJar` outputs, unless set to true in the `rosConfig`.  

**NOTE:** This means it is your responsibility to configure the obfuscator to ignore any shaded packages (if that is your wish), because they will be in the jar file (the uber-jar) that gets sent to the obfuscation server.

**Required Build Configuration**  
You will also need to add the following line in your `build.gradle.kts` file, as it defines the relationship between the `shadowJar` output and `rosObfuscateJar`. Otherwise, Gradle will error about the relationship between these tasks.
```kotlin
tasks.named("rosObfuscateJar").get().dependsOn(tasks.shadowJar)
```


## Gradle Tasks
#### `rosObfuscateJar`
ROS adds an obfuscation task that you can run directly, or add onto the build task for automated obfuscation.  
**Note:** Currently this task has no inputs and will only use the ros config with the output of the default gradle `jar` tasks. More specifically:
- This task requires the `apiUrl` ros config option (like all ros tasks)
- This task may use the `configFilePath` ros config option (depending on obfuscator type) in order to send additional obfuscation config to the backend.
- The exact output of the `jar` task is sent, along with any provided obfuscator config files, to the ros backend. The obfuscated file that returns is stored back at the original location (overwriting the jar)

**Additional Build Configuration**  
The `rosObfuscateJar` contains one additional property that can be configured. This property mimics the properties of the other jar tasks.  
```kotlin
tasks {
    named<io.github.jake_moore.ros_plugin.tasks.ObfuscateJarTask>("rosObfuscateJar") {
        // archiveClassifier is set to "obf" by default
        // This controls the suffix for your output file
        archiveClassifier.set("obfuscated")
        // Will produce a jar formmated "<...>-obfuscated.jar"
    }
}
```

#### `rosGetWatermark`
The obfuscation process in ROS adds light watermarking to each obfuscated jar it returns. This includes a basic requestID and the email of the user who made the request.  
In order to fetch this watermark data, you must send jars to the `/api/watermark` endpoint, or use the `rosGetWatermark` task.  

To run this task, you must provide a path to the jar file you wish to analyze. This can be done using:
```bash
./gradlew rosGetWatermark --jar="/full/path/to/your/java-program.jar"
```

Successful responses look like:
```
Watermark Successfully Extracted!
        Request ID: <epoch>-<short-uuid>
        Request User: user@example.com
```

#### `rosGetStackTrace`
ROS acknowledges that obfuscating programs makes developing them a bit harder. For example, an obfuscated jar will produce an obfuscated stacktrace for any errors it throws. Many obfuscators provide methods to restore the stack-trace methods/classes/line-numbers. For supported obfuscators, the `/api/stacktrace` endpoint is available on the backend to handle stack trace resolution.  
(For more info on how this endpoint works, check out the implementation of this task)  

To run this task, you must provide two argument:
1. A `--requestId=<id>` OR `--jar=<path>`
2. A `--trace=<path>`

If you use the --jar path (for simplicity) the ros plugin will use the watermark endpoint to extract the requestID, then continue as if you had provided a requestID in the command.

Command Example using `--jar`:
```bash
./gradlew rosGetStackTrace --jar='/full/path/to/your/java-program.jar' --trace='/path/to/your/trace-file.txt'
```

Command Example using `--requestId`:
```bash
./gradlew rosGetStackTrace --requestId='<id>' --trace='/path/to/your/trace-file.txt'
```

Successful responses look like:
```
Stack Trace Translated Successfully!
Stack Trace:
----------------------------------------------------------------
<your original stack trace, with obf names reverted>
----------------------------------------------------------------
```