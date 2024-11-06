package io.github.jake_moore.ros_plugin;

import io.github.jake_moore.ros_plugin.tasks.ObfuscateJarTask;
import io.github.jake_moore.ros_plugin.tasks.StackTraceTask;
import io.github.jake_moore.ros_plugin.tasks.WatermarkTask;
import okhttp3.OkHttpClient;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;

@SuppressWarnings("unused")
public class ROSGradlePlugin implements Plugin<Project> {
    public static final OkHttpClient client = new OkHttpClient();

    @Override
    public void apply(Project project) {
        // Register the extension for configuration
        ROSGradleConfig config = project.getExtensions().create("rosConfig", ROSGradleConfig.class);

        // Have the default `jar` and `shadowJar` task re-run if its output jar file doesn't exist
        // This is because we will sometimes delete the file, and it should be recreated if it doesn't exist
        // Without this, these tasks will not recreate the deleted file, and `rosObfuscateJar` will fail to find the expected file
        fixJarTasks(project, config);

        // Register the obfuscateJar task, which specifically uses the JAR task output, as its input
        project.getTasks().register("rosObfuscateJar", ObfuscateJarTask.class, task -> {
            task.dependsOn("jar");
            task.setConfig(config);
        });

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

    private void fixJarTasks(Project project, ROSGradleConfig config) {
        // Add an up-to-date check to the jar task
        // Here, we ensure that the jar file exists, if not the jar task cannot be up-to-date
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        File jarOutputFile = jarTask.getArchiveFile().get().getAsFile();
        jarTask.getOutputs().upToDateWhen(task -> jarOutputFile.exists());

        if (project.getTasks().findByName("shadowJar") != null) {
            // Add an up-to-date check to the shadowJar task
            // Here, we ensure that the shadowJar file exists, if not the shadowJar task cannot be up-to-date
            Jar shadowJarTask = (Jar) project.getTasks().getByName("shadowJar");
            File shadowJarOutputFile = shadowJarTask.getArchiveFile().get().getAsFile();
            shadowJarTask.getOutputs().upToDateWhen(task -> shadowJarOutputFile.exists());
        }

        // Add a lifecycle hook to ensure Jar tasks are up-to-date
        project.getTasks().whenTaskAdded(task -> {
            if (!(task instanceof Jar jTask)) { return; }

            // Apply upToDateWhen condition
            File jTaskOutputFile = jTask.getArchiveFile().get().getAsFile();
            task.getOutputs().upToDateWhen(t -> jTaskOutputFile.exists());
        });
    }
}
