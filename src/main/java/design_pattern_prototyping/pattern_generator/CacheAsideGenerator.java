package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheAsideGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CacheAsideGenerator.class.getName());
    private String tempConfigPath;
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/CacheAside/cache-config.yml";
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
            KubernetesUtil.applyYaml(tempConfigPath, NAMESPACE);

            // Step 2: Deploy Redis cache
            KubernetesUtil.applyYaml("src/main/resources/Patterns/CacheAside/redis-cache-deployment.yml", NAMESPACE);

            // Step 3: Deploy the NGINX proxy for cache-aside
            KubernetesUtil.applyYaml("src/main/resources/Patterns/CacheAside/nginx-cache-config.yml", NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/Patterns/CacheAside/nginx-proxy-deployment.yml", NAMESPACE);

            logger.info("Cache-Aside Pattern setup completed successfully.");

            // Delete temp config file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Cache-Aside Pattern.", e);
        }
    }
}