package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

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
    private static final String ENVOY_IMAGE = "envoyproxy/envoy:v1.30-latest";
    private static final int ENVOY_PORT = 8081;
    private String redisReplicaCount = "2";
    private String redisClusterNodes = "6";

    private final List<String> tempDeploymentPaths = new ArrayList<>();
    private final List<String> tempServicePaths = new ArrayList<>();
    private final List<String> tempEnvoyConfig = new ArrayList<>();

    @Override
    public void generatePattern(Map<String, String> parameters) {
        generatePattern(List.of(parameters));
    }

    @Override
    public void generatePattern(List<Map<String, String>> configs) {
        // Clear old files
        tempDeploymentPaths.clear();
        tempServicePaths.clear();
        tempEnvoyConfig.clear();

        // Generate Pattern
        try {
            buildDockerImage("src/main/resources/Patterns/CacheAside/httpcache/Dockerfile", "cache-proxy-async:1.0");
            loadImageMinikube("cache-proxy-async:1.0");

            // Update Instance redis variables to be used in buildPattern
            if (!configs.isEmpty()) {
                updateRedisSettingsFromConfig(configs.get(0));
            }

            for (Map<String, String> entry : configs) {
                String serviceName = entry.get("BACKEND_SERVICE");
                String deploymentName = "cache-proxy-"+serviceName;
                String port = entry.get("BACKEND_PORT");
                String endpoints = entry.get("CACHED_ENDPOINTS");
                String maxConnections = entry.get("MAX_CONNECTIONS");
                String ttl = entry.get("CACHE_TTL");

                // Fetch Deployments and inject Envoy sidecar
                Path tempDeployFile = Files.createTempFile("deployment-" + serviceName + "-", ".yml");
                KubernetesUtil.getDeploymentYamlToFile(serviceName, "user", tempDeployFile);
                injectEnvoySidecar(tempDeployFile, "envoy-config-" + serviceName, ENVOY_IMAGE);
                Files.deleteIfExists(tempDeployFile);

                // Envoy ConfigMap Generation
                Path envoyConfigPath = generateEnvoyConfigMap(serviceName, port, endpoints, deploymentName);
                tempEnvoyConfig.add(envoyConfigPath.toString());
                logger.info("Temporary Envoy ConfigMap YAML generated at: " + envoyConfigPath);

                // Patch the service targetPort to envoy port
                patchServicePorts(serviceName, ENVOY_PORT, Integer.parseInt(port));

                // Generate proxy deployment YAML for each backend
                String proxyYaml = Files.readString(Paths.get(PROXY_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", serviceName)
                        .replace("${BACKEND_PORT}", port)
                        .replace("${CACHE_TTL}", ttl)
                        .replace("${MAX_CONNECTIONS}", maxConnections)
                        .replace("${CACHED_ENDPOINTS}", endpoints);

                Path tempProxyFile = Files.createTempFile("proxy-deployment-" + serviceName + "-", ".yml");
                Files.writeString(tempProxyFile, proxyYaml);
                tempDeploymentPaths.add(tempProxyFile.toString());

                // Generate Proxy Service YAML for each backend
                String proxyServiceYaml = Files.readString(Paths.get(PROXY_SERVICE))
                        .replace("${BACKEND_SERVICE}", serviceName);

                Path tempProxyService = Files.createTempFile("proxy-service-" + serviceName, ".yml");
                Files.writeString(tempProxyService, proxyServiceYaml);
                tempServicePaths.add(tempProxyService.toString());
            }

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to generate Cache-Aside pattern config", e);
        }
    }

    private void updateRedisSettingsFromConfig(Map<String, String> config) {
        redisReplicaCount = config.getOrDefault("REDIS_REPLICAS", redisReplicaCount);
        redisClusterNodes = config.getOrDefault("REDIS_NODES", redisClusterNodes);
    }

    @Override
    public void deployPattern() {
        try {
            // Deploy Redis
            KubernetesUtil.executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            KubernetesUtil.executeCommand("helm", "repo", "update");
            KubernetesUtil.executeCommand("helm", "upgrade", "--install", "redis-cache", "bitnami/redis-cluster",
                    "-n", "pattern",
                    "--create-namespace",
                    "--set", "usePassword=false",
                    "--set", "replica.replicaCount=" + redisReplicaCount,
                    "--set", "cluster.nodes=" + redisClusterNodes);

            // Apply envoy configmaps
            for (String envoyPath : tempEnvoyConfig) {
                try {
                    KubernetesUtil.applyYaml(envoyPath);
                } catch (IOException e) {
                    throw new IOException("Failed to apply envoy config YAML: " + envoyPath, e);
                }
            }

            // Apply proxy services
            for (String proxyPath : tempServicePaths) {
                try {
                    KubernetesUtil.applyYaml(proxyPath, NAMESPACE);
                } catch (IOException e) {
                    throw new IOException("Failed to apply proxy service YAML: " + proxyPath, e);
                }
            }

            // Apply proxy deployments
            for (String proxyPath : tempDeploymentPaths) {
                try {
                    KubernetesUtil.applyYaml(proxyPath, NAMESPACE);
                } catch (IOException e) {
                    throw new IOException("Failed to apply proxy deployment YAML: " + proxyPath, e);
                }
            }

            logger.info("Cache-Aside Pattern deployed successfully.");

            // Cleanup temp files
            for (String proxyPath : tempServicePaths) {
                Files.deleteIfExists(Paths.get(proxyPath));
            }
            for (String proxyPath : tempDeploymentPaths) {
                Files.deleteIfExists(Paths.get(proxyPath));
            }
            for (String configPath : tempEnvoyConfig) {
                Files.deleteIfExists(Paths.get(configPath));
            }

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Deployment failed for Cache-Aside pattern", e);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Deployment interrupted", e);
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

    private Path generateEnvoyConfigMap(String backendName, String backendPort, String endpoints, String deploymentName) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        List<Map<String, Object>> clusters = new ArrayList<>();
        List<Map<String, Object>> routes = new ArrayList<>();

        // Pattern cluster
        String patternService = deploymentName + "." + NAMESPACE + ".svc.cluster.local";

        Map<String, Object> patternCluster = Map.of(
                "name", deploymentName,
                "connect_timeout", "1s",
                "type", "STRICT_DNS",
                "lb_policy", "ROUND_ROBIN",
                "load_assignment", Map.of(
                        "cluster_name", deploymentName,
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", patternService,
                                                                "port_value", 80
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );

        // Add one route per cache endpoint
        if (endpoints != null && !endpoints.isBlank()) {
            for (String endpoint : endpoints.split(",")) {
                endpoint = endpoint.trim();
                if (!endpoint.isEmpty()) {
                    Map<String, Object> patternRoute = Map.of(
                            "match", Map.of("prefix", endpoint),
                            "route", Map.of("cluster", deploymentName)
                    );
                    routes.add(patternRoute);
                }
            }
        }

        clusters.add(patternCluster);

        // Default Cluster
        Map<String, Object> backendCluster = Map.of(
                "name", backendName,
                "connect_timeout", "1s",
                "type", "STRICT_DNS",
                "lb_policy", "ROUND_ROBIN",
                "load_assignment", Map.of(
                        "cluster_name", backendName,
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", "127.0.0.1",
                                                                "port_value", Integer.parseInt(backendPort)
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );

        Map<String, Object> defaultRoute = Map.of(
                "match", Map.of("prefix", "/"),
                "route", Map.of("cluster", backendName)
        );

        clusters.add(backendCluster);
        routes.add(defaultRoute);

        // OTEL Collector cluster (required for tracing)
        Map<String, Object> otelCollectorCluster = Map.of(
                "name", "opentelemetry_collector",
                "type", "STRICT_DNS",
                "lb_policy", "ROUND_ROBIN",
                "typed_extension_protocol_options", Map.of(
                        "envoy.extensions.upstreams.http.v3.HttpProtocolOptions", Map.of(
                                "@type", "type.googleapis.com/envoy.extensions.upstreams.http.v3.HttpProtocolOptions",
                                "explicit_http_config", Map.of(
                                        "http2_protocol_options", new HashMap<>()
                                )
                        )
                ),
                "load_assignment", Map.of(
                        "cluster_name", "opentelemetry_collector",
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", "otel-collector.otel.svc.cluster.local",
                                                                "port_value", 4317
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );
        clusters.add(otelCollectorCluster);

        // Listener with Envoy OpenTelemetry tracer
        Map<String, Object> listener = Map.of(
                "name", "listener_http",
                "address", Map.of("socket_address", Map.of("address", "0.0.0.0", "port_value", ENVOY_PORT)),
                "filter_chains", List.of(Map.of(
                        "filters", List.of(Map.of(
                                "name", "envoy.filters.network.http_connection_manager",
                                "typed_config", Map.of(
                                        "@type", "type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager",
                                        "stat_prefix", "ingress_http",
                                        "codec_type", "AUTO",
                                        "tracing", Map.of(
                                                "provider", Map.of(
                                                        "name", "envoy.tracers.opentelemetry",
                                                        "typed_config", Map.of(
                                                                "@type", "type.googleapis.com/envoy.config.trace.v3.OpenTelemetryConfig",
                                                                "grpc_service", Map.of(
                                                                        "envoy_grpc", Map.of("cluster_name", "opentelemetry_collector"),
                                                                        "timeout", "0.250s"
                                                                ),
                                                                "service_name", "envoy-sidecar"
                                                        )
                                                )
                                        ),
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

    public void injectEnvoySidecar(Path yamlFile, String envoyConfigMapName, String envoyImage) throws IOException, InterruptedException {
        LoaderOptions loadOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loadOptions));
        Map<String, Object> originalYaml;

        try (InputStream input = Files.newInputStream(yamlFile)) {
            originalYaml = yaml.load(input);
        }

        Map<String, Object> spec = (Map<String, Object>) ((Map<String, Object>) originalYaml.get("spec")).get("template");
        Map<String, Object> podSpec = (Map<String, Object>) spec.get("spec");
        List<Map<String, Object>> containers = (List<Map<String, Object>>) podSpec.get("containers");

        Map<String, Object> envoyContainer = new LinkedHashMap<>();
        envoyContainer.put("name", "envoy");
        envoyContainer.put("image", envoyImage);
        envoyContainer.put("ports", List.of(Map.of("containerPort", ENVOY_PORT)));
        envoyContainer.put("command", List.of("envoy"));
        envoyContainer.put("args", List.of("-c", "/etc/envoy/envoy.yaml", "--log-level", "warn"));
        envoyContainer.put("volumeMounts", List.of(Map.of(
                "name", "envoy-config",
                "mountPath", "/etc/envoy",
                "readOnly", true
        )));
        containers.add(envoyContainer);

        List<Map<String, Object>> volumes = (List<Map<String, Object>>) podSpec.get("volumes");
        if (volumes == null) {
            volumes = new ArrayList<>();
            podSpec.put("volumes", volumes);
        }
        volumes.add(Map.of(
                "name", "envoy-config",
                "configMap", Map.of("name", envoyConfigMapName)
        ));

        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml outputYaml = new Yaml(dumperOptions);

        Path updatedFile = Files.createTempFile("deployment-with-envoy", ".yml");
        try (BufferedWriter writer = Files.newBufferedWriter(updatedFile)) {
            outputYaml.dump(originalYaml, writer);
        }

        Map<String, Object> metadata = (Map<String, Object>) originalYaml.get("metadata");
        String name = (String) metadata.get("name");
        String namespace = metadata.containsKey("namespace") ? (String) metadata.get("namespace") : "user";

        new ProcessBuilder("kubectl", "delete", "deployment", name, "-n", namespace).inheritIO().start().waitFor();
        new ProcessBuilder("kubectl", "apply", "-f", updatedFile.toAbsolutePath().toString()).inheritIO().start().waitFor();

        System.out.println("Envoy sidecar injected and deployment applied: " + name);
    }

    private void patchServicePorts(String serviceName, int envoyTargetPort, int backendTargetPort) throws IOException, InterruptedException {
        logger.info("Patching service " + serviceName + " to point to envoy");

        // xport existing service YAML
        Path originalYaml = Files.createTempFile("svc-" + serviceName, ".yaml");
        KubernetesUtil.getServiceYamlToFile(serviceName, "user", originalYaml);

        // Load YAML using SnakeYAML
        LoaderOptions loadOptions = new LoaderOptions();
        Yaml yaml = new Yaml(new SafeConstructor(loadOptions));
        try (InputStream input = Files.newInputStream(originalYaml)) {
            Map<String, Object> data = yaml.load(input);

            // Navigate to spec.ports
            Map<String, Object> spec = (Map<String, Object>) data.get("spec");

            List<Map<String, Object>> ports = new ArrayList<>();

            // Envoy port
            Map<String, Object> envoyPort = new LinkedHashMap<>();
            envoyPort.put("name", "envoy");
            envoyPort.put("port", 8080);
            envoyPort.put("protocol", "TCP");
            envoyPort.put("targetPort", envoyTargetPort);
            ports.add(envoyPort);

            // App Port
            Map<String, Object> backendPort = new LinkedHashMap<>();
            backendPort.put("name", "backend");
            backendPort.put("port", 8082);
            backendPort.put("protocol", "TCP");
            backendPort.put("targetPort", backendTargetPort);
            ports.add(backendPort);

            spec.put("ports", ports);

            // Dump modified YAML
            Path updatedYaml = Files.createTempFile("patched-svc-" + serviceName, ".yaml");

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Representer representer = new Representer(options);
            Yaml outputYaml = new Yaml(representer, options);

            try (Writer writer = Files.newBufferedWriter(updatedYaml)) {
                outputYaml.dump(data, writer);
            }

            // Apply modified YAML
            KubernetesUtil.applyYaml(updatedYaml.toString(), "user");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to patch service YAML with SnakeYAML", e);
        }
    }
}