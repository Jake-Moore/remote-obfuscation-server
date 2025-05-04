package io.github.jake_moore.ros_plugin.tasks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jake_moore.ros_plugin.ROSGradleConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static io.github.jake_moore.ros_plugin.ROSGradlePlugin.client;

@Setter @Getter
public class ObfuscateJarTask extends DefaultTask {
    @Setter @Internal
    private @Nullable ROSGradleConfig config = null;

    public ObfuscateJarTask() {
        // Obfuscation is NEVER up-to-date, it can always be re-run to get a new obfuscated JAR
        getOutputs().upToDateWhen(task -> false);
    }

    @InputFile
    public File getInputJar() {
        // Retrieve the jar task output file, or the shadowJar output if that plugin is installed
        // Check if a shadowJar task exists, and if so, use its output file
        if (getProject().getTasks().findByName("shadowJar") != null) {
            return getProject().getTasks().getByName("shadowJar").getOutputs().getFiles().getSingleFile();
        }
        // Otherwise, use the default jar task output file
        return getProject().getTasks().getByName("jar").getOutputs().getFiles().getSingleFile();
    }

    @Input
    public final Property<String> archiveClassifier = getProject().getObjects().property(String.class);

    @OutputFile
    public File getObfuscatedJar() {
        // Resolve the obfuscated JAR file dynamically
        String name = getProject().getName();
        String version = getProject().getVersion().toString();
        String classifier = archiveClassifier.getOrElse("obf");

        // Set the output file path
        File buildDir = getProject().getLayout().getBuildDirectory().getAsFile().get();
        String outputFileName = String.format("%s-%s%s.jar", name, version, classifier.isEmpty() ? "" : "-" + classifier);
        return new File(buildDir, "libs/" + outputFileName);
    }

    @SneakyThrows
    @TaskAction
    public void sendJar() {
        ROSGradleConfig config = getProject().getExtensions().findByType(ROSGradleConfig.class);
        if (config == null) throw new RuntimeException("ROSGradleConfig not found!");

        try {
            sendRequest(getInputJar(), getObfuscatedJar(), config);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to send JAR for obfuscation", t);
        }
    }

    @SuppressWarnings("unused")
    private void sendRequest(@NotNull File inputJar, @NotNull File outputJar, @NotNull ROSGradleConfig config) throws IOException {
        final String REQUEST_URL = config.getObfuscationEndpoint();
        final File configFile = new File(config.getConfigFilePath().get());
        if (!configFile.exists()) {
            throw new RuntimeException("Config file does not exist: " + configFile.getAbsolutePath());
        }
        if (!inputJar.exists()) {
            throw new RuntimeException("JAR file (from jar task) does not exist: " + inputJar.getAbsolutePath());
        }

        boolean keepOriginalJar = config.getKeepOriginalJar().getOrElse(false); // Delete the original jar by default
        File uploadFile = keepOriginalJar ? inputJar : null;

        // Personal Choice: let's copy the `jar` output to a temporary file, so that we can delete the original JAR file if requested
        // This allows the jar to be removed from /build/libs faster, and reduces the likelihood of it being used by anyone looking for the build jar
        if (!keepOriginalJar) {
            File tempJarFile = File.createTempFile(inputJar.getName(), ".jar");
            Files.copy(inputJar.toPath(), tempJarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // We should now delete all input files (from tasks: `jar`, `shadowJar`) to prevent them from being used
            @NotNull List<File> deletedFiles = deleteInputFiles(inputJar);
            if (!deletedFiles.isEmpty()) {
                System.out.println("\n----------------------------------------------------------------");
                System.out.println("Deleted original JAR files:");
                for (File deletedFile : deletedFiles) {
                    System.out.println("\t" + deletedFile.getAbsolutePath());
                }
                System.out.println("You can disable this behavior by setting `keepOriginalJar` to true in the ros config.");
                System.out.println("----------------------------------------------------------------\n");
            }

            // Replace our jarFile field, so that from now on we use the temporary file instead
            uploadFile = tempJarFile;
        }

        // Get the username you want to include (e.g., from environment or system properties)
        @Nullable String githubUser = System.getenv("GITHUB_ACTOR");

        // Create the URL with the query parameter
        @NotNull String obfuscateJarURL = REQUEST_URL;
        if (githubUser != null && !githubUser.isEmpty()) {
            obfuscateJarURL += "?requested_by=" + URLEncoder.encode(githubUser, StandardCharsets.UTF_8);
        }

        // Create multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("jar", uploadFile.getName(),
                        RequestBody.create(uploadFile, MediaType.parse(Files.probeContentType(uploadFile.toPath()))))
                .addFormDataPart("config", configFile.getName(),
                        RequestBody.create(configFile, MediaType.parse(Files.probeContentType(configFile.toPath()))))
                .build();

        // Get auth token from environment variable
        String authToken = System.getenv("ROS_GITHUB_PAT");
        if (authToken == null) {
            throw new RuntimeException("ROS_GITHUB_PAT environment variable not set!");
        }

        String requestID;
        // Initial request to start obfuscation
        try (Response response = client.newCall(new Request.Builder()
                .url(obfuscateJarURL)
                .header("Authorization", "Bearer " + authToken)
                .post(requestBody)
                .build()).execute()) {
            
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Unexpected code (" + response.code() + "): " + responseBody);
            }

            // Parse the JSON response to get the request ID
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            requestID = jsonResponse.get("request_id").getAsString();
            System.out.println("Obfuscation job started with ID: " + requestID);
            @Nullable JsonElement queue_index = jsonResponse.get("queue_index");
            @Nullable JsonElement total_queue_size = jsonResponse.get("total_queue_size");
            int queue_index_val = queue_index != null ? queue_index.getAsInt() : -1;
            if (queue_index == null || queue_index_val < 0 || total_queue_size == null) {
                System.out.println("No queue position information available.");
            } else {
                // Print the queue position
                System.out.println("Current queue position: " + (queue_index_val + 1) + "/" + total_queue_size.getAsInt());
            }
        }

        // Poll for completion
        final int DEFAULT_MAX_ATTEMPTS = 60; // 5 minutes max (60 * 5 seconds)
        final int DEFAULT_POLL_INTERVAL_MS = 5000; // 5 seconds

        final int maxAttempts = config.getPollMaxAttempts().getOrElse(DEFAULT_MAX_ATTEMPTS);
        final int pollIntervalMs = config.getPollIntervalMs().getOrElse(DEFAULT_POLL_INTERVAL_MS);
        int attempts = 0;

        // Continue to poll obfuscation status until we have a definite answer, or we run out of attempts
        while (attempts < maxAttempts) {
            // Still processing
            System.out.println("Waiting for obfuscation to complete... " + (attempts + 1) + "/" + maxAttempts);

            // Sleep a bit before fetching obfuscation results
            sleep(pollIntervalMs);

            try (Response response = client.newCall(new Request.Builder()
                    .url(REQUEST_URL + "/" + requestID)
                    .header("Authorization", "Bearer " + authToken)
                    .get()
                    .build()).execute()) {

                String responseBody = response.body().string();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Unexpected code (" + response.code() + "): " + responseBody);
                }

                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                String status = jsonResponse.get("status").getAsString();

                // Log Status
                System.out.println("Current Obfuscation status (" + (attempts + 1) + "/" + maxAttempts + "): " + status);

                if ("completed".equals(status)) {
                    // Log start of Jar copy
                    System.out.println("\nObfuscation completed, writing JAR to: " + outputJar.getAbsolutePath());

                    // If the file already exists, we should delete it
                    if (outputJar.exists()) {
                        if (outputJar.delete()) {
                            System.out.println("\tDeleted existing obfuscated JAR file: " + outputJar.getAbsolutePath());
                        } else {
                            throw new RuntimeException("Failed to delete existing obfuscated JAR file: " + outputJar.getAbsolutePath());
                        }
                    }

                    String DOWNLOAD_URL = REQUEST_URL + "/" + requestID + "/download";

                    // Download the obfuscated JAR file
                    try (Response downloadResponse = client.newCall(new Request.Builder()
                            .url(DOWNLOAD_URL)
                            .header("Authorization", "Bearer " + authToken)
                            .get()
                            .build()).execute()) {

                        if (!downloadResponse.isSuccessful()) {
                            throw new RuntimeException("Failed to download JAR: " + downloadResponse.code());
                        }

                        ResponseBody body = downloadResponse.body();

                        // Get the content length for progress tracking
                        long bytesRead = 0;

                        try (InputStream inputStream = body.byteStream();
                             FileOutputStream outputStream = new FileOutputStream(outputJar)) {

                            byte[] buffer = new byte[8192];
                            int read;

                            while ((read = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, read);
                                bytesRead += read;
                            }
                        }

                        System.out.println("\tObfuscated Jar written to: " + outputJar.getAbsolutePath());
                        System.out.println("\tRequest ID: " + requestID);
                    }
                    return;
                } else if ("failed".equals(status)) {
                    throw new RuntimeException("Obfuscation failed: " + jsonResponse.get("error").getAsString());
                } else {
                    // Log Queue Position Information
                    @Nullable JsonElement queue_index = jsonResponse.get("queue_index");
                    @Nullable JsonElement total_queue_size = jsonResponse.get("total_queue_size");
                    int queue_index_val = queue_index != null ? queue_index.getAsInt() : -1;
                    if (queue_index == null || queue_index_val < 0 || total_queue_size == null) {
                        System.out.println("\tNo queue position information available.");
                    } else {
                        // Print the queue position
                        System.out.println("\tCurrent queue position: " + (queue_index_val + 1) + "/" + total_queue_size.getAsInt());
                    }
                    attempts++;
                }
            }
        }

        throw new RuntimeException("Obfuscation timed out after " + (maxAttempts * pollIntervalMs / 1000) + " seconds");
    }

    private static void sleep(int pollIntervalMs) {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for obfuscation", e);
        }
    }

    @NotNull
    private List<File> deleteInputFiles(@NotNull File inputJar) {
        List<File> deletedFiles = new ArrayList<>();
        if (inputJar.exists()) {
            if (!inputJar.delete()) {
                throw new RuntimeException("Failed to delete original inputJar file: " + inputJar.getAbsolutePath());
            }
            deletedFiles.add(inputJar);
        }

        // Get the output from the `jar` task
        File jarOutputFile = getProject().getTasks().getByName("jar").getOutputs().getFiles().getSingleFile();
        if (jarOutputFile.exists()) {
            if (!jarOutputFile.delete()) {
                throw new RuntimeException("Failed to delete original `jar` task output file: " + jarOutputFile.getAbsolutePath());
            }
            deletedFiles.add(jarOutputFile);
        }

        // If the shadowJar plugin is installed, delete its output file as well
        if (getProject().getTasks().findByName("shadowJar") != null) {
            File shadowJarOutputFile = getProject().getTasks().getByName("shadowJar").getOutputs().getFiles().getSingleFile();
            if (shadowJarOutputFile.exists()) {
                if (!shadowJarOutputFile.delete()) {
                    throw new RuntimeException("Failed to delete original `shadowJar` task output file: " + shadowJarOutputFile.getAbsolutePath());
                }
                deletedFiles.add(shadowJarOutputFile);
            }
        }

        return deletedFiles;
    }
}
