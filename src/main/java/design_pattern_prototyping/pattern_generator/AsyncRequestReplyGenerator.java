package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

public class AsyncRequestReplyGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(AsyncRequestReplyGenerator.class.getName());
    private final List<String> tempEnvoyConfigs = new ArrayList<>();
    private final List<String> tempEnvoyDeployments = new ArrayList<>();
    private final List<String> tempEnvoyServices = new ArrayList<>();
    private final List<String> tempProxyDeployments = new ArrayList<>();
    private final List<String> tempProxyServices = new ArrayList<>();
    private final List<String> tempListenerPaths = new ArrayList<>();

    private static final String NAMESPACE = "pattern";

    private static final String LISTENER_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/listener/listener-deployment-template.yml";
    private static final String PROXY_SERVICE_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-service-template.yml";
    private static final String PROXY_DEPLOYMENT_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-deployment-template.yml";
    private static final String ENVOY_SERVICE_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/envoy/envoy-service-template.yml";
    private static final String ENVOY_DEPLOYMENT_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/envoy/envoy-deployment-template.yml";
    private static final String ENVOY_CONFIG_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/envoy/envoy-configmap-template.yml";
    private static final String REDIS_CACHE_YAML = "src/main/resources/Patterns/AsyncRequestReply/proxy/redis-cache-deployment.yml";

    @Override
    public void generatePattern(Map<String, String> parameters) {
        generatePattern(Collections.singletonList(parameters));
    }

    @Override
    public void generatePattern(List<Map<String, String>> configs) {
        tempEnvoyConfigs.clear();
        tempEnvoyDeployments.clear();
        tempEnvoyServices.clear();
        tempProxyDeployments.clear();
        tempProxyServices.clear();
        tempListenerPaths.clear();

        try {
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/proxy/Dockerfile.proxy", "proxy-service:local");
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/listener/Dockerfile.listener", "listener-service:local");
            loadImageMinikube("proxy-service:local");
            loadImageMinikube("listener-service:local");

            Map<String, List<String>> serviceToPaths = new HashMap<>();
            Map<String, String> serviceToPort = new HashMap<>();
            Set<String> renamedServices = new HashSet<>();

            for (Map<String, String> entry : configs) {
                String backendName = entry.get("BACKEND_NAME");
                String backendPort = entry.get("BACKEND_PORT");
                String path = entry.get("ENDPOINT_PATH");

                logger.info("Mapping: " + backendName + " -> " + path + " (port: " + backendPort + ")");

                serviceToPaths.computeIfAbsent(backendName, k -> new ArrayList<>()).add(path);
                serviceToPort.put(backendName, backendPort);

                if (!renamedServices.contains(backendName)) {
                    renameBackendService(backendName);
                    renamedServices.add(backendName);
                }
            }

            for (String backendName : serviceToPaths.keySet()) {
                List<String> paths = serviceToPaths.get(backendName);
                String backendPort = serviceToPort.get(backendName);
                String joinedPaths = String.join(",", paths);

                String deploymentName = "proxy-" + backendName;

                // Listener (per service, using first path for URL)
                String listenerName = "listener-" + backendName;
                String listenerYaml = Files.readString(Paths.get(LISTENER_TEMPLATE))
                        .replace("${LISTENER_NAME}", listenerName)
                        .replace("${BACKEND_NAME}", backendName)
                        .replace("${BACKEND_PORT}", backendPort)
                        .replace("${ENDPOINT_PATHS}", joinedPaths);

                Path tempListener = Files.createTempFile("listener-" + backendName, ".yml");
                Files.writeString(tempListener, listenerYaml);
                tempListenerPaths.add(tempListener.toString());

                // Proxy Deployment
                String proxyDeploymentYaml = Files.readString(Paths.get(PROXY_DEPLOYMENT_TEMPLATE))
                        .replace("${DEPLOYMENT_NAME}", deploymentName)
                        .replace("${BACKEND_NAME}", backendName)
                        .replace("${BACKEND_PORT}", backendPort)
                        .replace("${ENDPOINT_PATHS}", joinedPaths);

                Path tempProxyDeployment = Files.createTempFile("proxy-deployment-" + backendName, ".yml");
                Files.writeString(tempProxyDeployment, proxyDeploymentYaml);
                tempProxyDeployments.add(tempProxyDeployment.toString());

                // Proxy Service
                String proxyServiceYaml = Files.readString(Paths.get(PROXY_SERVICE_TEMPLATE))
                        .replace("${DEPLOYMENT_NAME}", deploymentName)
                        .replace("${BACKEND_NAME}", backendName)
                        .replace("${BACKEND_PORT}", backendPort);

                Path tempProxyService = Files.createTempFile("proxy-service-" + backendName, ".yml");
                Files.writeString(tempProxyService, proxyServiceYaml);
                tempProxyServices.add(tempProxyService.toString());

                // Envoy ConfigMap generation
                Path envoyPath = generateEnvoyConfigMap(backendName, backendPort, paths, deploymentName);
                tempEnvoyConfigs.add(envoyPath.toString());

                // Envoy Service
                String envoyServiceYaml = Files.readString(Paths.get(ENVOY_SERVICE_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", backendName)
                        .replace("${BACKEND_PORT}", backendPort);

                Path tempEnvoyService= Files.createTempFile("envoy-Service-" + backendName, ".yml");
                Files.writeString(tempEnvoyService, envoyServiceYaml);
                tempEnvoyServices.add(tempEnvoyService.toString());

                // Envoy Deployment
                String envoyDeploymentYaml = Files.readString(Paths.get(ENVOY_DEPLOYMENT_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", backendName)
                        .replace("${BACKEND_PORT}", backendPort);

                Path tempEnvoyDeployment = Files.createTempFile("envoy-deployment-" + backendName, ".yml");
                Files.writeString(tempEnvoyDeployment, envoyDeploymentYaml);
                tempEnvoyDeployments.add(tempEnvoyDeployment.toString());
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Pattern generation failed", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            KubernetesUtil.executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            KubernetesUtil.executeCommand("helm", "repo", "update");
            KubernetesUtil.executeCommand("helm", "upgrade", "--install", "rabbitmq", "bitnami/rabbitmq",
                    "--set", "auth.username=user,auth.password=bitnami", "--namespace", NAMESPACE);

            KubernetesUtil.createNamespace("proxy");
            KubernetesUtil.applyYaml(REDIS_CACHE_YAML);

            for (String yaml : tempEnvoyConfigs) {
                KubernetesUtil.applyYaml(yaml);
            }

            for (String yaml : tempEnvoyServices) {
                KubernetesUtil.applyYaml(yaml);
            }

            for (String yaml : tempEnvoyDeployments) {
                KubernetesUtil.applyYaml(yaml);
            }

            for (String yaml : tempProxyDeployments) {
                KubernetesUtil.applyYaml(yaml);
            }

            for (String yaml : tempProxyServices) {
                KubernetesUtil.applyYaml(yaml);
            }

            for (String yaml : tempListenerPaths) {
                KubernetesUtil.applyYaml(yaml);
            }

            // Cleanup
            for (String yaml : tempEnvoyConfigs) Files.deleteIfExists(Paths.get(yaml));
            for (String yaml : tempEnvoyServices) Files.deleteIfExists(Paths.get(yaml));
            for (String yaml : tempEnvoyDeployments) Files.deleteIfExists(Paths.get(yaml));
            for (String yaml : tempProxyServices) Files.deleteIfExists(Paths.get(yaml));
            for (String yaml : tempProxyDeployments) Files.deleteIfExists(Paths.get(yaml));
            for (String yaml : tempListenerPaths) Files.deleteIfExists(Paths.get(yaml));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Pattern deployment failed", e);
        }
    }

    private void buildDockerImage(String dockerfilePath, String imageName) {
        try {
            Path dockerfile = Paths.get(dockerfilePath);
            String buildContext = dockerfile.getParent().toString();

            logger.info("Building Docker image: " + imageName);
            Process process = new ProcessBuilder("docker", "build", "-t", imageName, "-f", dockerfilePath, buildContext)
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

    private Path generateEnvoyConfigMap(String backendName, String backendPort, List<String> endpointPaths, String proxyDeploymentName) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        List<Map<String, Object>> clusters = new ArrayList<>();
        List<Map<String, Object>> routes = new ArrayList<>();

        // Proxy routes for given endpoint paths
        for (String path : endpointPaths) {
            String clusterName = proxyDeploymentName + path.replace("/", "-");
            String proxyService = proxyDeploymentName + ".user.svc.cluster.local";

            Map<String, Object> cluster = Map.of(
                    "name", clusterName,
                    "connect_timeout", "1s",
                    "type", "STRICT_DNS",
                    "lb_policy", "ROUND_ROBIN",
                    "load_assignment", Map.of(
                            "cluster_name", clusterName,
                            "endpoints", List.of(Map.of(
                                    "lb_endpoints", List.of(Map.of(
                                            "endpoint", Map.of(
                                                    "address", Map.of(
                                                            "socket_address", Map.of(
                                                                    "address", proxyService,
                                                                    "port_value", Integer.parseInt(backendPort)
                                                            )
                                                    )
                                            )
                                    ))
                            ))
                    )
            );

            Map<String, Object> route = Map.of(
                    "match", Map.of("prefix", path),
                    "route", Map.of("cluster", clusterName)
            );

            clusters.add(cluster);
            routes.add(route);
        }

        // Default/fallback cluster to original backend
        String backendClusterName = backendName + "-backend";
        String backendService = backendClusterName + ".user.svc.cluster.local";

        Map<String, Object> backendCluster = Map.of(
                "name", backendClusterName,
                "connect_timeout", "1s",
                "type", "STRICT_DNS",
                "lb_policy", "ROUND_ROBIN",
                "load_assignment", Map.of(
                        "cluster_name", backendClusterName,
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", backendService,
                                                                "port_value", Integer.parseInt(backendPort)
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );

        Map<String, Object> defaultRoute = Map.of(
                "match", Map.of("prefix", "/"),  // Catch-all fallback
                "route", Map.of("cluster", backendClusterName)
        );

        clusters.add(backendCluster);
        routes.add(defaultRoute);  // Add fallback route last

        // Define listener with updated route list
        Map<String, Object> listener = Map.of(
                "name", "listener_http",
                "address", Map.of("socket_address", Map.of("address", "0.0.0.0", "port_value", 8080)),
                "filter_chains", List.of(Map.of(
                        "filters", List.of(Map.of(
                                "name", "envoy.filters.network.http_connection_manager",
                                "typed_config", Map.of(
                                        "@type", "type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager",
                                        "stat_prefix", "ingress_http",
                                        "codec_type", "AUTO",
                                        "route_config", Map.of(
                                                "name", "local_route",
                                                "virtual_hosts", List.of(Map.of(
                                                        "name", "default-vh",
                                                        "domains", List.of("*"),
                                                        "routes", routes
                                                ))
                                        ),
                                        "http_filters", List.of(Map.of(
                                                "name", "envoy.filters.http.router",
                                                "typed_config", Map.of(
                                                        "@type", "type.googleapis.com/envoy.extensions.filters.http.router.v3.Router"
                                                )
                                        ))
                                )
                        ))
                ))
        );

        Map<String, Object> finalConfig = Map.of("static_resources", Map.of(
                "clusters", clusters,
                "listeners", List.of(listener)
        ));

        String envoyYaml = yaml.dump(finalConfig);
        Map<String, Object> configMap = Map.of(
                "apiVersion", "v1",
                "kind", "ConfigMap",
                "metadata", Map.of("name", "envoy-config-" + backendName, "namespace", "user"),
                "data", Map.of("envoy.yaml", envoyYaml)
        );

        Path tempFile = Files.createTempFile("envoy-config-" + backendName, ".yml");
        try (Writer writer = Files.newBufferedWriter(tempFile)) {
            yaml.dump(configMap, writer);
        }

        return tempFile;
    }
}