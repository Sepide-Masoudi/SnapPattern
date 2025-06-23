package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheAsideGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(CacheAsideGenerator.class.getName());
    private static final String CONFIG_TEMPLATE = "src/main/resources/Patterns/CacheAside/cache-config-base.yml";
    private static final String PROXY_TEMPLATE = "src/main/resources/Patterns/CacheAside/nginx-proxy-deployment.yml";
    private static final String REDIS_DEPLOYMENT = "src/main/resources/Patterns/CacheAside/redis-cache-deployment.yml";
    private static final String NAMESPACE = "pattern";

    private final List<String> tempProxyPaths = new ArrayList<>();
    private String tempConfigMapPath;

    @Override
    public String getYamlFilePath() {
        return CONFIG_TEMPLATE;
    }

    @Override
    public void generatePattern(String filePath, List<Map<String, String>> configs) {
        try {
            // Build combined ConfigMap
            Map<String, Object> configMap = new LinkedHashMap<>();
            configMap.put("apiVersion", "v1");
            configMap.put("kind", "ConfigMap");
            configMap.put("metadata", Map.of("name", "cache-config", "namespace", NAMESPACE));

            Map<String, String> data = new LinkedHashMap<>();

            for (Map<String, String> entry : configs) {
                String service = entry.get("BACKEND_SERVICE");
                String endpoints = entry.get("CACHED_ENDPOINTS");

                data.put(service + "_CACHED_ENDPOINTS", endpoints);
                data.put(service + "_BACKEND_SERVICE", service);

                // Generate proxy deployment YAML for this service
                String proxyYaml = Files.readString(Paths.get(PROXY_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", service);
                Path tempProxyFile = Files.createTempFile("proxy-" + service + "-", ".yml");
                Files.writeString(tempProxyFile, proxyYaml);
                tempProxyPaths.add(tempProxyFile.toString());
            }

            configMap.put("data", data);

            // Dump ConfigMap YAML to temp file
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml yaml = new Yaml(options);

            Path tempConfigFile = Files.createTempFile("cache-config-", ".yml");
            try (Writer writer = Files.newBufferedWriter(tempConfigFile)) {
                yaml.dump(configMap, writer);
            }
            tempConfigMapPath = tempConfigFile.toString();

            logger.info("Generated combined cache-config at: " + tempConfigMapPath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to generate Cache-Aside pattern config", e);
        }
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        generatePattern(filePath, List.of(parameters));
    }

    @Override
    public void deployPattern() {
        try {
            if (tempConfigMapPath == null) {
                throw new IOException("Temp config path is null. Pattern not generated.");
            }

            // Apply ConfigMap
            KubernetesUtil.applyYaml(tempConfigMapPath, NAMESPACE);

            // Deploy Redis once
            KubernetesUtil.applyYaml(REDIS_DEPLOYMENT, NAMESPACE);

            // Deploy each proxy service
            for (String proxyPath : tempProxyPaths) {
                KubernetesUtil.applyYaml(proxyPath, NAMESPACE);
            }

            logger.info("Cache-Aside Pattern deployed successfully.");

            // Cleanup temp files
            Files.deleteIfExists(Paths.get(tempConfigMapPath));
            for (String proxyPath : tempProxyPaths) {
                Files.deleteIfExists(Paths.get(proxyPath));
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Deployment failed for Cache-Aside pattern", e);
        }
    }
    private void buildDockerImage(String dockerfilePath, String imageName) {
        try {
            Path dockerfile = Paths.get(dockerfilePath);
            String buildContext = dockerfile.getParent().toString();

            logger.info("Building Docker image: " + imageName);
            Process process = new ProcessBuilder(
                    "docker", "build", "-t", imageName, "-f", dockerfilePath, buildContext)
                    .inheritIO()
                    .start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Docker image built: " + imageName);
            } else {
                logger.warning("Failed to build Docker image: " + imageName);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error building Docker image: " + imageName, e);
        }
    }

    private void loadImageMinikube(String imageName) {
        try {
            logger.info("Loading image into Minikube: " + imageName);
            Process process = new ProcessBuilder("minikube", "image", "load", imageName).inheritIO().start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("Image loaded into Minikube: " + imageName);
            } else {
                logger.warning("Failed to load image into Minikube: " + imageName);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error loading image into Minikube: " + imageName, e);
        }
    }
}