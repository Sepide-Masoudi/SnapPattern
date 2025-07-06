package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheAsideGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(CacheAsideGenerator.class.getName());
    private static final String PROXY_SERVICE = "src/main/resources/Patterns/CacheAside/httpcache/proxy-service.yml";
    private static final String PROXY_TEMPLATE = "src/main/resources/Patterns/CacheAside/httpcache/proxy-deployment.yml";
    private static final String NAMESPACE = "pattern";
    private String redisReplicaCount = "2";
    private String redisClusterNodes = "6";

    private final List<String> tempDeploymentPaths = new ArrayList<>();
    private final List<String> tempServicePaths = new ArrayList<>();

    @Override
    public void generatePattern(String filePath,Map<String, String> parameters) {
        generatePattern(null,List.of(parameters));
    }

    @Override
    public void generatePattern(String filePath,List<Map<String, String>> configs) {
        // Clear old files
        tempDeploymentPaths.clear();
        tempServicePaths.clear();

        try {
            buildDockerImage("src/main/resources/Patterns/CacheAside/httpcache/Dockerfile.proxy", "cache-proxy-async:1.0");
            loadImageMinikube("cache-proxy-async:1.0");

            if (!configs.isEmpty()) {
                Map<String, String> firstConfig = configs.get(0);
                redisReplicaCount = firstConfig.getOrDefault("REDIS_REPLICAS", redisReplicaCount);
                redisClusterNodes = firstConfig.getOrDefault("REDIS_NODES", redisClusterNodes);
            }


            // Rename each backend service
       /*     for (Map<String, String> entry : configs) {
                String backendName = entry.get("BACKEND_SERVICE");
                renameBackendService(backendName);
            }*/


            for (Map<String, String> entry : configs) {
                String service = entry.get("BACKEND_SERVICE"); //+ ".user.svc.cluster.local";
                //String service = entry.get("BACKEND_SERVICE");
                String port = entry.get("BACKEND_PORT");
                String endpoints = entry.get("CACHED_ENDPOINTS");
                String maxConnections = entry.get("MAX_CONNECTIONS");
                String ttl = entry.get("CACHE_TTL");

                // Generate proxy deployment YAML for each backend
                String proxyYaml = Files.readString(Paths.get(PROXY_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", service)
                        .replace("${BACKEND_PORT}", port)
                        .replace("${CACHE_TTL}", ttl)
                        .replace("${MAX_CONNECTIONS}", maxConnections)
                        .replace("${CACHED_ENDPOINTS}", endpoints)
                        .replace("${NAMESPACE}", NAMESPACE);

                Path tempProxyFile = Files.createTempFile("proxy-deployment-" + service + "-", ".yml");
                Files.writeString(tempProxyFile, proxyYaml);
                tempDeploymentPaths.add(tempProxyFile.toString());

                // Generate Proxy Service YAML for each backend
                String proxyServiceYaml = Files.readString(Paths.get(PROXY_SERVICE))
                        .replace("${SERVICE_NAME}", service)
                        .replace("${SERVICE_PORT}", port)
                        .replace("${NAMESPACE}", NAMESPACE);

                Path tempProxyService = Files.createTempFile("proxy-service-" + service, ".yml");
                Files.writeString(tempProxyService, proxyServiceYaml);
                tempServicePaths.add(tempProxyService.toString());
            }

        } catch (IOException e /*| InterruptedException e*/) {
            logger.log(Level.SEVERE, "Failed to generate Cache-Aside pattern config", e);
        }
    }

    @Override
    public void deployPattern() {
        try {

            // Deploy Redis
            KubernetesUtil.executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            KubernetesUtil.executeCommand("helm", "repo", "update");
            KubernetesUtil.executeCommand("helm", "upgrade", "-install", "redis-cache", "bitnami/redis-cluster",
                    "-n", "pattern",
                    "--create-namespace",
                    "--set", "usePassword=false",
                    "--set", "replica.replicaCount=2",
                    "--set", "cluster.nodes=6");

            // Deploy each proxy service
            for (String proxyPath : tempServicePaths) {
                KubernetesUtil.applyYaml(proxyPath, NAMESPACE);
            }

            // Deploy each proxy deployment
            for (String proxyPath : tempDeploymentPaths) {
                KubernetesUtil.applyYaml(proxyPath, NAMESPACE);
            }

            logger.info("Cache-Aside Pattern deployed successfully.");

            // Cleanup temp files
            for (String proxyPath : tempServicePaths) {
                Files.deleteIfExists(Paths.get(proxyPath));
            }

            for (String proxyPath : tempDeploymentPaths) {
                Files.deleteIfExists(Paths.get(proxyPath));
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Deployment failed for Cache-Aside pattern", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getYamlFilePath() {
        return null;
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

    private void renameBackendService(String serviceName) throws IOException, InterruptedException {
        Path svcPath = Paths.get("svc-" + serviceName + ".yaml");

        // Get original YAML
        KubernetesUtil.getServiceYamlToFile(serviceName, "user", svcPath);

        // Delete the original service
        KubernetesUtil.executeCommand("kubectl", "delete", "svc", serviceName, "-n", "user");

        // Modify the service name
        List<String> lines = Files.readAllLines(svcPath);
        List<String> modifiedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().startsWith("name:")) {
                modifiedLines.add("  name: " + serviceName + "-backend");
            } else {
                modifiedLines.add(line);
            }
        }
        Files.write(svcPath, modifiedLines);

        // Apply updated YAML
        KubernetesUtil.executeCommand("kubectl", "apply", "-f", svcPath.toString());


    }


}