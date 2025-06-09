package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayOffloadingGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(GatewayOffloadingGenerator.class.getName());
    private String tempConfigPath;
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/GatewayOffloading/nginx-ingress.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            logger.info("Loading template: " + filePath);
            Path templatePath = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(templatePath));

            // Replace placeholders with user-defined values
            //yamlContent = yamlContent.replace("${SERVICE_HOST}", parameters.getOrDefault("SERVICE_HOST", "default-host"));
            yamlContent = yamlContent.replace("${SERVICE_ENDPOINT}", parameters.getOrDefault("SERVICE_ENDPOINT", "/default-endpoint"));
            yamlContent = yamlContent.replace("${SERVICE_NAME}", parameters.getOrDefault("SERVICE_NAME", "default-service"));
            yamlContent = yamlContent.replace("${SERVICE_PORT}", parameters.getOrDefault("SERVICE_PORT", "8080"));

            // Create temp file to store the modified YAML
            Path tempFile = Files.createTempFile("gateway-offloading-config-", ".yml");
            Files.write(tempFile, yamlContent.getBytes());
            tempConfigPath = tempFile.toString();

            logger.info("Temporary Gateway Offloading pattern config generated at: " + tempFile);

            // Store the temporary file path for use in deployment
            parameters.put("TEMP_CONFIG_PATH", tempFile.toString());

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating Gateway Offloading pattern configuration.", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            if (tempConfigPath == null || tempConfigPath.isEmpty()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Step 1: Add Helm repositories and update
            executeCommand("helm", "repo", "add", "ingress-nginx", "https://kubernetes.github.io/ingress-nginx");
            executeCommand("helm", "repo", "update");

            // Step 3: Deploy NGINX Ingress Controller
            executeCommand("helm", "install", "nginx-ingress", "ingress-nginx/ingress-nginx", "--namespace", "pattern",
                    "--set", "controller.admissionWebhooks.enabled=false");

            // Step 4: Apply the generated Gateway Offloading YAML
            KubernetesUtil.applyYaml(tempConfigPath, NAMESPACE);

            logger.info("Gateway Offloading Pattern setup completed successfully.");

            // Delete temporary file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Gateway Offloading Pattern.", e);
        }
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        logger.info("Executing command: " + String.join(" ", command));
        ProcessBuilder processBuilder = new ProcessBuilder(command).inheritIO();
        Process process = processBuilder.start();
        process.waitFor();
    }
}