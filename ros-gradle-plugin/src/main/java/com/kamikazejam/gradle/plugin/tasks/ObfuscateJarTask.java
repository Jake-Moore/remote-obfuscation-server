package com.kamikazejam.gradle.plugin.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kamikazejam.gradle.plugin.ROSGradleConfig;
import static com.kamikazejam.gradle.plugin.ROSGradlePlugin.client;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ObfuscateJarTask {

    public static void sendJar(@NotNull File jarFile, @NotNull ROSGradleConfig config) {
        try {
            sendRequest(jarFile, config);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void sendRequest(@NotNull File jarFile, @NotNull ROSGradleConfig config) throws IOException {
        final String REQUEST_URL = config.getObfuscationEndpoint();
        final File configFile = new File(config.getConfigFilePath().get());
        if (!configFile.exists()) {
            throw new RuntimeException("Config file does not exist: " + configFile.getAbsolutePath());
        }

        // Create multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("jar", jarFile.getName(),
                        RequestBody.create(jarFile, MediaType.parse(Files.probeContentType(jarFile.toPath()))))
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
            if (!response.isSuccessful()) {
                throw new RuntimeException("Unexpected code " + response);
            }
            String responseBody = response.body().string();

            // Parse the JSON response
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            String requestID = jsonResponse.get("request_id").getAsString();
            String obfuscatorOutputBase64 = jsonResponse.get("obfuscator_output").getAsString();

            // Decode base64-encoded obfuscator output
            String obfuscatorOutput = new String(Base64.getDecoder().decode(obfuscatorOutputBase64));
            System.out.println("Obfuscator output: " + obfuscatorOutput);

            // Save the base64-encoded JAR file in place of the original JAR
            jarFile.delete();

            byte[] outputFileBytes = Base64.getDecoder().decode(jsonResponse.get("output_file").getAsString());
            Files.write(jarFile.toPath(), outputFileBytes);

            System.out.println("Obfuscated Jar written to: " + jarFile.getAbsolutePath());
            System.out.println("\tRequest ID: " + requestID);
        }
    }
}
