package io.github.jake_moore.ros_plugin.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jake_moore.ros_plugin.ROSGradleConfig;
import io.github.jake_moore.ros_plugin.util.Either;
import io.github.jake_moore.ros_plugin.util.WatermarkData;
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

import static io.github.jake_moore.ros_plugin.ROSGradlePlugin.client;

@SuppressWarnings("unused")
public class StackTraceTask extends DefaultTask {
    private static final String JAR_OPTION_NAME = "jar";
    private static final String REQUEST_ID_OPTION_NAME = "requestId";
    private static final String STACK_TRACE_OPTION_NAME = "trace";
    private static final String OUTPUT_OPTION_NAME = "output";

    @Setter
    private @Nullable ROSGradleConfig config = null;
    private @Nullable Either<File, String> either; // Either a File (obfuscated jar) or a String (requestID)
    private @Nullable String stackTracePath = null;
    private @Nullable String outputFilePath = null;

    @Option(option = REQUEST_ID_OPTION_NAME, description = "The request ID of the obfuscated jar that produced the stack trace.")
    public void setRequestId(String requestId) {
        if (this.either != null) {
            throw new RuntimeException("Cannot specify both --" + REQUEST_ID_OPTION_NAME + " and --" + JAR_OPTION_NAME + " in the task invocation.");
        }
        if (requestId == null) {
            throw new RuntimeException("Please specify the request ID using --" + REQUEST_ID_OPTION_NAME + "=<id> in the task invocation.");
        }
        this.either = Either.right(requestId);
    }

    @Option(option = JAR_OPTION_NAME, description = "The path to the obfuscated jar file that produced the stack trace.")
    public void setJarFilePath(String jarFilePath) {
        if (this.either != null) {
            throw new RuntimeException("Cannot specify both --" + REQUEST_ID_OPTION_NAME + " and --" + JAR_OPTION_NAME + " in the task invocation.");
        }
        if (jarFilePath == null) {
            throw new RuntimeException("Please specify the jar file path using --" + JAR_OPTION_NAME + "=<path> in the task invocation.");
        }
        this.either = Either.left(new File(jarFilePath));
    }

    @Option(option = STACK_TRACE_OPTION_NAME, description = "The path to the stack trace file.")
    public void setStackTracePath(@Nullable String stackTracePath) {
        this.stackTracePath = stackTracePath;
    }

    @Option(option = OUTPUT_OPTION_NAME, description = "The optional file path to the write the translated stacktrace to.")
    public void setOutputFilePath(@Nullable String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    @TaskAction
    public void readWatermark() {
        if (either == null) {
            throw new RuntimeException("Please specify either --" + REQUEST_ID_OPTION_NAME + " or --" + JAR_OPTION_NAME + " in the task invocation.");
        }
        if (config == null) {
            throw new RuntimeException("Please config ros config (apiUrl) in the build file.");
        }
        if (stackTracePath == null) {
            throw new RuntimeException("Please specify the stack trace file path using --" + STACK_TRACE_OPTION_NAME + "=<path> in the task invocation.");
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
            throw new RuntimeException("The specified stack trace file does not exist.");
        }
        if (stackTraceFile.isDirectory()) {
            throw new RuntimeException("The specified stack trace file path is a directory, not a file.");
        }

        try {
            translateStackTrace(config, stackTraceFile, requestId, authToken);
        }catch (IOException e) {
            throw new RuntimeException(e);
        }
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
