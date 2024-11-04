package com.kamikazejam.gradle.plugin.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kamikazejam.gradle.plugin.ROSGradleConfig;
import com.kamikazejam.gradle.plugin.util.Either;
import com.kamikazejam.gradle.plugin.util.WatermarkData;
import lombok.Setter;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;

import static com.kamikazejam.gradle.plugin.ROSGradlePlugin.client;

@SuppressWarnings("unused")
public class StackTraceTask extends DefaultTask {
    @Setter
    private @Nullable ROSGradleConfig config = null;
    private @Nullable Either<File, String> either; // Either a File (obfuscated jar) or a String (requestID)
    private @Nullable String stackTracePath = null;
    private @Nullable String outputFilePath = null;

    @Option(option = "requestId", description = "The request ID of the obfuscated jar that produced the stack trace.")
    public void setRequestId(String requestId) {
        if (this.either != null) {
            throw new IllegalArgumentException("Cannot specify both --requestId and --jar in the task invocation.");
        }
        if (requestId == null) {
            throw new IllegalArgumentException("Please specify the request ID using --requestId=<id> in the task invocation.");
        }
        this.either = Either.right(requestId);
    }

    @Option(option = "jar", description = "The path to the obfuscated jar file that produced the stack trace.")
    public void setJarFilePath(String jarFilePath) {
        if (this.either != null) {
            throw new IllegalArgumentException("Cannot specify both --requestId and --jar in the task invocation.");
        }
        if (jarFilePath == null) {
            throw new IllegalArgumentException("Please specify the jar file path using --jar=<path> in the task invocation.");
        }
        this.either = Either.left(new File(jarFilePath));
    }

    @Option(option = "trace", description = "The path to the stack trace file.")
    public void setStackTracePath(@Nullable String stackTracePath) {
        this.stackTracePath = stackTracePath;
    }

    @Option(option = "output", description = "The optional file path to the write the translated stacktrace to.")
    public void setOutputFilePath(@Nullable String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    @TaskAction
    public void readWatermark() {
        try {
            readWatermarkI();
        }catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void readWatermarkI() throws Throwable {
        if (either == null) {
            throw new IllegalArgumentException("Please specify either --requestId or --jar in the task invocation.");
        }
        if (config == null) {
            throw new IllegalArgumentException("Please config ros config (apiUrl) in the build file.");
        }
        if (stackTracePath == null) {
            throw new IllegalArgumentException("Please specify the stack trace file path using --stackTrace=<path> in the task invocation.");
        }

        // Get auth token from environment variable
        String authToken = System.getenv("ROS_GITHUB_PAT");
        if (authToken == null) {
            throw new RuntimeException("ROS_GITHUB_PAT environment variable not set!");
        }

        @NotNull final String requestId = either.right().orElseGet(() -> {
            assert either.left().isPresent();
            File jarFile = either.left().get();
            try {
                @NotNull WatermarkData watermark = WatermarkTask.getWatermarkFromFile(config, jarFile);
                System.out.println("Watermark Extracted from Input Jar!");
                System.out.println("\tRequest ID: " + watermark.getRequestId());
                System.out.println("\tRequest User: " + watermark.getRequestUser() + "\n");
                return watermark.getRequestId();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Validate stack trace path provided
        File stackTraceFile = new File(stackTracePath);
        if (!stackTraceFile.exists()) {
            throw new IllegalArgumentException("The specified stack trace file does not exist.");
        }
        if (stackTraceFile.isDirectory()) {
            throw new IllegalArgumentException("The specified stack trace file path is a directory, not a file.");
        }

        translateStackTrace(config, stackTraceFile, requestId, authToken);
    }

    private void translateStackTrace(ROSGradleConfig config, File stackTraceFile, String requestId, String token) throws IOException {
        // Read the stack trace file content
        System.out.println("Reading stack trace from " + stackTraceFile.getAbsolutePath());
        String stacktrace = Files.readString(stackTraceFile.toPath());

        // Encode stacktrace to base64
        String encodedTrace = Base64.getEncoder().encodeToString(stacktrace.getBytes());

        // Create JSON object with request ID and encoded stack trace
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("request_id", requestId);
        jsonBody.addProperty("stack_trace_base64", encodedTrace);

        // Build the request
        RequestBody requestBody = RequestBody.create(
                jsonBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(config.getStackTraceEndpoint())
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + token)
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            JsonObject jsonObject = JsonParser.parseString(response.body().string()).getAsJsonObject();
            String outputBase64 = jsonObject.get("output_trace_base64").getAsString();
            String outputTrace = new String(Base64.getDecoder().decode(outputBase64));

            System.out.println("Stack Trace Translated Successfully!");
            System.out.println("Stack Trace: ");
            System.out.println("----------------------------------------------------------------");
            System.out.println(outputTrace);
            System.out.println("----------------------------------------------------------------");

            if (outputFilePath != null) {
                File outputFile = new File(outputFilePath);
                Files.writeString(outputFile.toPath(), outputTrace);
                System.out.println("\nOutput trace written to " + outputFile.getAbsolutePath());
            }
        }
    }

}
