package io.github.jake_moore.ros_plugin.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jake_moore.ros_plugin.ROSGradleConfig;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import okhttp3.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
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

        // Create request with authorization header
        Request request = new Request.Builder()
                .url(REQUEST_URL)
                .header("Authorization", "Bearer " + authToken)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Unexpected code (" + response.code() + "): " + responseBody);
            }

            // Parse the JSON response
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            String requestID = jsonResponse.get("request_id").getAsString();
            String obfuscatorOutputBase64 = jsonResponse.get("obfuscator_output").getAsString();

            // Decode base64-encoded obfuscator output
            String obfuscatorOutput = new String(Base64.getDecoder().decode(obfuscatorOutputBase64));
            System.out.println("Obfuscator output: " + obfuscatorOutput);

            // We will save our obfuscated file to a separate -obf jar file, so that the original `jar` task doesn't
            //  mistake it for the original JAR file and consider it cached.

            // If the file already exists, we should delete it
            if (outputJar.exists()) {
                if (outputJar.delete()) {
                    System.out.println("Deleted existing obfuscated JAR file: " + outputJar.getAbsolutePath());
                }else {
                    throw new RuntimeException("Failed to delete existing obfuscated JAR file: " + outputJar.getAbsolutePath());
                }
            }

            // Translate the base64-encoded JAR string back into a standard file at our desired location
            byte[] outputFileBytes = Base64.getDecoder().decode(jsonResponse.get("output_file").getAsString());
            Files.write(outputJar.toPath(), outputFileBytes);

            System.out.println("Obfuscated Jar written to: " + outputJar.getAbsolutePath());
            System.out.println("\tRequest ID: " + requestID);
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
