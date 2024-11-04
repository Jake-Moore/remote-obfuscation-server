package com.kamikazejam.gradle.plugin.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kamikazejam.gradle.plugin.ROSGradleConfig;
import com.kamikazejam.gradle.plugin.util.WatermarkData;
import lombok.Setter;
import okhttp3.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static com.kamikazejam.gradle.plugin.ROSGradlePlugin.client;

@SuppressWarnings("unused")
public class WatermarkTask extends DefaultTask {
    private @Nullable String jarFilePath = null;
    @Setter
    private @Nullable ROSGradleConfig config = null;

    @Option(option = "jar", description = "The file path for the jar to read the watermark from.")
    public void setJarFilePath(@Nullable String jarFilePath) {
        this.jarFilePath = jarFilePath;
    }

    @TaskAction
    public void readWatermark() {
        if (jarFilePath == null) {
            throw new IllegalArgumentException("Please specify the jar file path using --jar=<path> in the task invocation.");
        }
        if (config == null) {
            throw new IllegalArgumentException("Please config ros config (apiUrl) in the build file.");
        }

        try {
            WatermarkData watermark = getWatermarkFromFile(config, new File(jarFilePath));
            System.out.println("Watermark Successfully Extracted!");
            System.out.println("\tRequest ID: " + watermark.getRequestId());
            System.out.println("\tRequest User: " + watermark.getRequestUser());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    public static WatermarkData getWatermarkFromFile(@NotNull ROSGradleConfig config, @NotNull File file) throws IOException {
        // Get auth token from environment variable
        String authToken = System.getenv("ROS_GITHUB_PAT");
        if (authToken == null) {
            throw new RuntimeException("ROS_GITHUB_PAT environment variable not set!");
        }

        if (!file.exists()) {
            throw new IllegalArgumentException("The specified jar file does not exist.");
        }
        if (file.isDirectory()) {
            throw new IllegalArgumentException("The specified jar file path is a directory, not a file.");
        }

        // Create multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("jar", file.getName(),
                        RequestBody.create(file, MediaType.parse(Files.probeContentType(file.toPath()))))
                .build();

        // Build the request
        Request request = new Request.Builder()
                .url(config.getWatermarkEndpoint())
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + authToken)
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            String requestId = jsonObject.get("request_id").getAsString();
            String requestUser = jsonObject.get("request_user").getAsString();
            return new WatermarkData(requestId, requestUser);
        }
    }
}
