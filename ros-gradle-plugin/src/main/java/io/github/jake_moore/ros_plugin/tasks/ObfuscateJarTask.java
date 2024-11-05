package io.github.jake_moore.ros_plugin.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jake_moore.ros_plugin.ROSGradleConfig;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import static io.github.jake_moore.ros_plugin.ROSGradlePlugin.client;

public class ObfuscateJarTask {

    public static void sendJar(@NotNull File jarFile, @NotNull ROSGradleConfig config) {
        try {
            sendRequest(jarFile, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void sendRequest(@NotNull File jarTaskOutput, @NotNull ROSGradleConfig config) throws IOException {
        final String REQUEST_URL = config.getObfuscationEndpoint();
        final File configFile = new File(config.getConfigFilePath().get());
        if (!configFile.exists()) {
            throw new RuntimeException("Config file does not exist: " + configFile.getAbsolutePath());
        }
        if (!jarTaskOutput.exists()) {
            throw new RuntimeException("JAR file (from jar task) does not exist: " + jarTaskOutput.getAbsolutePath());
        }

        boolean keepOriginalJar = config.getKeepOriginalJar().getOrElse(false); // Delete the original jar by default
        File uploadFile = keepOriginalJar ? jarTaskOutput : null;

        // Personal Choice: let's copy the `jar` output to a temporary file, so that we can delete the original JAR file if requested
        // This allows the jar to be removed from /build/libs faster, and reduces the likelihood of it being used by anyone looking for the build jar
        if (!keepOriginalJar) {
            File tempJarFile = File.createTempFile(jarTaskOutput.getName(), ".jar");
            Files.copy(jarTaskOutput.toPath(), tempJarFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // Now that the data is copied, we should delete the original file
            if (jarTaskOutput.delete()) {
                System.out.println("Deleted original JAR file: " + jarTaskOutput.getAbsolutePath());
                System.out.println("\tYou can disable this behavior by setting `keepOriginalJar` to true in the ros config.");
            }else {
                throw new RuntimeException("Failed to delete original JAR file: " + jarTaskOutput.getAbsolutePath());
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
            final String obfJarFileName = jarTaskOutput.getName().substring(0, jarTaskOutput.getName().lastIndexOf('.')) + "-obf.jar";
            File obfuscatedJarFile = new File(jarTaskOutput.getParentFile(), obfJarFileName);

            // If the file already exists, we should delete it
            if (obfuscatedJarFile.exists()) {
                if (obfuscatedJarFile.delete()) {
                    System.out.println("Deleted existing obfuscated JAR file: " + obfuscatedJarFile.getAbsolutePath());
                }else {
                    throw new RuntimeException("Failed to delete existing obfuscated JAR file: " + obfuscatedJarFile.getAbsolutePath());
                }
            }

            // Translate the base64-encoded JAR string back into a standard file at our desired location
            byte[] outputFileBytes = Base64.getDecoder().decode(jsonResponse.get("output_file").getAsString());
            Files.write(obfuscatedJarFile.toPath(), outputFileBytes);

            System.out.println("Obfuscated Jar written to: " + obfuscatedJarFile.getAbsolutePath());
            System.out.println("\tRequest ID: " + requestID);
        }
    }
}
