package com.example.design_pattern_prototyping.pattern_generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CircuitBreakerGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CircuitBreakerGenerator.class.getName());
    private String tempConfigPath;

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/CircuitBreaker/circuit-breaker-retry.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            logger.info("Loading template: " + filePath);
            Path templatePath = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(templatePath));

            // Replace placeholders with user-defined values
            yamlContent = yamlContent.replace("${SERVICE_NAME}", parameters.getOrDefault("SERVICE_NAME", "default-service"));
            yamlContent = yamlContent.replace("${MAX_PENDING_REQUESTS}", parameters.getOrDefault("MAX_PENDING_REQUESTS", "5"));
            yamlContent = yamlContent.replace("${MAX_CONNECTIONS}", parameters.getOrDefault("MAX_CONNECTIONS", "1"));
            yamlContent = yamlContent.replace("${FAILURE_THRESHOLD}", parameters.getOrDefault("FAILURE_THRESHOLD", "5"));
            yamlContent = yamlContent.replace("${RETRY_ATTEMPTS}", parameters.getOrDefault("RETRY_ATTEMPTS", "3"));

            // Create temp file to store the modified YAML
            Path tempFile = Files.createTempFile("circuit-breaker-retry-", ".yml");
            Files.write(tempFile, yamlContent.getBytes());
            tempConfigPath = tempFile.toString();

            logger.info("Temporary Circuit Breaker pattern config generated at: " + tempFile);

            // Store the temporary file path for use in deployment
            parameters.put("TEMP_CONFIG_PATH", tempFile.toString());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating Circuit Breaker pattern configuration.", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            if (tempConfigPath == null || tempConfigPath.isEmpty()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Step 1: Install istio control plane
            logger.info("Installing Istio control plane...");
            //executeCommand("istioctl", "install", "--set", "profile=default", "--set", "values.global.platform=minikube", "--skip-confirmation");
            executeCommand("kubectl", "rollout", "restart", "deployment", "-n", "user");

            // Step 2: Enable istio sidecar injection
            executeCommand("kubectl", "label", "namespace", "user", "istio-injection=enabled", "--overwrite");

            // Step 3: Apply the generated Circuit Breaker YAML
            applyYamlFile(tempConfigPath);

            logger.info("Circuit Breaker and retry pattern setup completed successfully.");

            // Delete temporary file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Circuit Breaker Pattern.", e);
        }
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[stdout] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stdout of process", e);
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.warning("[stderr] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stderr of process", e);
            }
        }).start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }

    /**
     * Helper method to apply a YAML file using kubectl.
     *
     * @param filePath The path to the YAML file to be applied.
     */
    private void applyYamlFile(String filePath) throws IOException, InterruptedException {
        logger.info("Applying configuration from file: " + filePath);
        ProcessBuilder apply = new ProcessBuilder("kubectl", "apply", "-f", filePath, "-n", "user");
        Process process = apply.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[KUBECTL OUTPUT] " + line);
            }
            while ((line = errorReader.readLine()) != null) {
                logger.warning("[KUBECTL ERROR] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("Successfully applied: " + filePath);
        } else {
            logger.severe("Failed to apply: " + filePath + " with exit code " + exitCode);
        }
    }
}