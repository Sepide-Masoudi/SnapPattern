package com.example.design_pattern_prototyping.pattern_generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheAsideGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CacheAsideGenerator.class.getName());
    private String tempConfigPath;

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/patterns/CacheAside/cache-config.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            logger.info("Loading template: " + filePath);
            Path templatePath = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(templatePath));

            // Replace placeholders with user-defined values
            yamlContent = yamlContent.replace("${CACHED_ENDPOINTS}", parameters.get("CACHED_ENDPOINTS"));
            yamlContent = yamlContent.replace("${BACKEND_SERVICE}", parameters.get("BACKEND_SERVICE"));

            // Create temp file to store the modified YAML
            Path tempFile = Files.createTempFile("cache-config-temp", ".yml");
            Files.write(tempFile, yamlContent.getBytes());

            tempConfigPath = tempFile.toString();
            logger.info("Temporary Cache-Aside pattern config generated at: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating Cache-Aside pattern configuration.", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            if (tempConfigPath == null || tempConfigPath.isEmpty()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Step 1: Deploy ConfigMap using the temporary config file
            applyYamlFile(tempConfigPath);

            // Step 2: Deploy Redis cache
            applyYamlFile("src/main/resources/patterns/CacheAside/redis-cache-deployment.yml");

            // Step 3: Deploy the NGINX proxy for cache-aside
            applyYamlFile("src/main/resources/patterns/CacheAside/nginx-cache-config.yml");
            applyYamlFile("src/main/resources/patterns/CacheAside/nginx-proxy-deployment.yml");

            logger.info("Cache-Aside Pattern setup completed successfully.");

            // Delete temp config file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Cache-Aside Pattern.", e);
        }
    }

    /**
     * Helper method to apply a YAML file using kubectl.
     *
     * @param filePath The path to the YAML file to be applied.
     */
    private void applyYamlFile(String filePath) throws IOException, InterruptedException {
        logger.info("Applying configuration from file: " + filePath);
        ProcessBuilder apply = new ProcessBuilder("kubectl", "apply", "-f", filePath, "-n", "pattern");
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