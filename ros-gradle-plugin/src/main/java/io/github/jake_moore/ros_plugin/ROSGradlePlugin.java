package io.github.jake_moore.ros_plugin;

import io.github.jake_moore.ros_plugin.tasks.ObfuscateJarTask;
import io.github.jake_moore.ros_plugin.tasks.StackTraceTask;
import io.github.jake_moore.ros_plugin.tasks.WatermarkTask;
import okhttp3.OkHttpClient;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;

@SuppressWarnings("unused")
public class ROSGradlePlugin implements Plugin<Project> {
    public static final OkHttpClient client = new OkHttpClient();

    @Override
    public void apply(Project project) {
        // Register the extension for configuration
        ROSGradleConfig config = project.getExtensions().create("rosConfig", ROSGradleConfig.class);

        // Register the obfuscateJar task, depending on the JAR task.
        TaskProvider<Task> obfuscateJarTask = project.getTasks().register("rosObfuscateJar", task -> {
            task.dependsOn("jar"); // Ensures `jar` runs first
            task.doLast(t -> {
                // Retrieve the original JAR file produced by the `jar` task
                Jar jarTask = (Jar) project.getTasks().getByName("jar");
                File originalJar = jarTask.getArchiveFile().get().getAsFile();

                // Call the Express API and get the obfuscated JAR
                ObfuscateJarTask.sendJar(originalJar, config);
            });
        });

        // Ensure that obfuscateJar is executed when the build task is run
        project.getTasks().getByName("build").dependsOn(obfuscateJarTask);



        // Register the watermarkJar task
        project.getTasks().create("rosGetWatermark", WatermarkTask.class, task -> {
            // Forward configuration to the task
            task.setConfig(config);
        });

        // Register the stacktrace task
        project.getTasks().create("rosGetStackTrace", StackTraceTask.class, task -> {
            // Forward configuration to the task
            task.setConfig(config);
        });
    }
}
