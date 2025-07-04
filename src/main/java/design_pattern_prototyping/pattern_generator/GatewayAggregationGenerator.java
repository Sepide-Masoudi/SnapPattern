package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayAggregationGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(GatewayAggregationGenerator.class.getName());
    private String tempConfigPath;
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/GatewayAggregation/nginx/gateway-aggregation-config.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            logger.info("Loading template: " + filePath);
            Path templatePath = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(templatePath));

            // Replace placeholders with user-defined values
            yamlContent = yamlContent.replace("${SERVICE_1_NAME}", parameters.get("SERVICE_1_NAME"));
            yamlContent = yamlContent.replace("${SERVICE_1_ENDPOINT}", parameters.get("SERVICE_1_ENDPOINT"));
            yamlContent = yamlContent.replace("${SERVICE_1_HOST}", parameters.get("SERVICE_1_HOST"));
            yamlContent = yamlContent.replace("${SERVICE_1_PORT}", parameters.get("SERVICE_1_PORT"));

            // Create temp file to store the modified YAML
            Path tempFile = Files.createTempFile("gateway-aggregation-config-", ".yml");
            Files.write(tempFile, yamlContent.getBytes());

            logger.info("Temporary Gateway Aggregation pattern config generated at: " + tempFile);

            // Store temp file path
            tempConfigPath = tempFile.toString();
            logger.info("TemporaryGateway Aggregation config generated at: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating Gateway Aggregation pattern configuration.", e);
        }
    }

    @Override
    public void deployPattern() throws InterruptedException {
        try {
            if (tempConfigPath == null || tempConfigPath.isEmpty()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Step 1: Apply ConfigMap with user parameters
            KubernetesUtil.applyYaml(tempConfigPath, NAMESPACE);

            // Step 2: Deploy the NGINX reverse proxy for Gateway Aggregation
            KubernetesUtil.applyYaml("src/main/resources/Patterns/GatewayAggregation/nginx/nginx-gateway-config.yml", NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/Patterns/GatewayAggregation/nginx/nginx-gateway-deployment.yml", NAMESPACE);

            logger.info("Gateway Aggregation Pattern setup completed successfully.");

            // Step 3: Delete temporary file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Gateway Aggregation Pattern.", e);
        }
    }
}