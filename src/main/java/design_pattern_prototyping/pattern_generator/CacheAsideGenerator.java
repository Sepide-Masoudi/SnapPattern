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
    private static final String PROXY_NAMESPACE = "pattern";   // namespace for proxies & Redis
    private static final String ENVOY_IMAGE = "envoyproxy/envoy:v1.30-latest";
    private static final int ENVOY_PORT = 8091;

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
        tempDeploymentPaths.clear();
        tempServicePaths.clear();
        tempEnvoyConfig.clear();

        try {
            // Build proxy image
            buildDockerImage("src/main/resources/Patterns/CacheAside/httpcache/Dockerfile", "cache-proxy-async:1.0");
            loadImageMinikube("cache-proxy-async:1.0");

            if (!configs.isEmpty()) {
                updateRedisSettingsFromConfig(configs.get(0));
            }

            for (Map<String, String> entry : configs) {
                String backendService = entry.get("BACKEND_SERVICE");
                String backendNS = entry.getOrDefault("BACKEND_NAMESPACE", "user");
                String deploymentName = "cache-proxy-" + backendService;
                String port = entry.get("BACKEND_PORT");
                String endpoints = entry.getOrDefault("CACHED_ENDPOINTS", "").trim();
                String maxConnections = entry.get("MAX_CONNECTIONS");
                String ttl = entry.get("CACHE_TTL");

                // Inject Envoy sidecar into backend deployment
                Path tmp = Files.createTempFile("deployment-" + backendService + "-", ".yml");
                KubernetesUtil.getDeploymentYamlToFile(backendService, backendNS, tmp);
                injectEnvoySidecar(tmp, "envoy-config-" + backendService, ENVOY_IMAGE, backendNS);
                Files.deleteIfExists(tmp);

                // Generate Envoy ConfigMap
                Path envoyCfg = generateEnvoyConfigMap(backendService, port, endpoints, deploymentName);
                tempEnvoyConfig.add(envoyCfg.toString());
                KubernetesUtil.applyYaml(envoyCfg.toString(), PROXY_NAMESPACE);

                // Patch backend Service ports
                patchServicePorts(backendService, backendNS, ENVOY_PORT, Integer.parseInt(port));

                // Prepare proxy deployment YAML
                String proxyYaml = Files.readString(Paths.get(PROXY_TEMPLATE))
                        .replace("${BACKEND_SERVICE}", backendService)
                        .replace("${BACKEND_PORT}", port)
                        .replace("${CACHE_TTL}", ttl)
                        .replace("${MAX_CONNECTIONS}", maxConnections)
                        .replace("${CACHED_ENDPOINTS}", endpoints);

                Path proxyDeploy = Files.createTempFile("proxy-deployment-" + backendService + "-", ".yml");
                Files.writeString(proxyDeploy, proxyYaml);
                tempDeploymentPaths.add(proxyDeploy.toString());

                // Prepare proxy service YAML
                String svcYaml = Files.readString(Paths.get(PROXY_SERVICE))
                        .replace("${BACKEND_SERVICE}", backendService);

                Path proxySvc = Files.createTempFile("proxy-service-" + backendService + "-", ".yml");
                Files.writeString(proxySvc, svcYaml);
                tempServicePaths.add(proxySvc.toString());
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
            // Install Redis cluster in PROXY_NAMESPACE
            KubernetesUtil.executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            KubernetesUtil.executeCommand("helm", "repo", "update");
            KubernetesUtil.executeCommand("helm", "upgrade", "--install", "redis-cache", "bitnami/redis-cluster",
                    "-n", PROXY_NAMESPACE,
                    "--create-namespace",
                    "--set", "usePassword=false",
                    "--set", "replica.replicaCount=" + redisReplicaCount,
                    "--set", "cluster.nodes=" + redisClusterNodes);

            // Apply proxy Services & Deployments in PROXY_NAMESPACE
            for (String svcPath : tempServicePaths) {
                KubernetesUtil.applyYaml(svcPath, PROXY_NAMESPACE);
            }
            for (String deployPath : tempDeploymentPaths) {
                KubernetesUtil.applyYaml(deployPath, PROXY_NAMESPACE);
            }

            logger.info("Cache-Aside Pattern deployed successfully.");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Deployment failed for Cache-Aside pattern", e);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Deployment interrupted", e);
        }
    }

    private void buildDockerImage(String dockerfilePath, String imageName) {
        try {
            Path df = Paths.get(dockerfilePath);
            String ctx = df.getParent().toString();
            logger.info("Building Docker image: " + imageName);
            Process p = new ProcessBuilder("docker", "build", "-t", imageName, "-f", dockerfilePath, ctx)
                    .inheritIO().start();
            p.waitFor();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error building Docker image", e);
        }
    }

    private void loadImageMinikube(String imageName) {
        try {
            logger.info("Loading image into Minikube: " + imageName);
            Process p = new ProcessBuilder("minikube", "image", "load", imageName)
                    .inheritIO().start();
            p.waitFor();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading Minikube image", e);
        }
    }

    private Path generateEnvoyConfigMap(String backendName,
                                        String backendPort,
                                        String endpoints,
                                        String deploymentName) throws IOException {
        // Prepare SnakeYAML options
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        // --- Build Clusters ---
        List<Map<String, Object>> clusters = new ArrayList<>();

        // 1) Envoy side‑car cluster (cache‑proxy)
        String cacheServiceFqdn = deploymentName + "." + PROXY_NAMESPACE + ".svc.cluster.local";
        Map<String, Object> cacheCluster = Map.of(
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
                                                                "address", cacheServiceFqdn,
                                                                "port_value", 80
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );
        clusters.add(cacheCluster);

        // 2) Backend cluster (your app)
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
        clusters.add(backendCluster);

        // 3) OTEL Collector cluster for tracing
        Map<String, Object> otelCluster = Map.of(
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
        clusters.add(otelCluster);

        // --- Build Routes ---
        List<Map<String, Object>> routes = new ArrayList<>();
        if (!endpoints.isBlank()) {
            for (String ep : endpoints.split(",")) {
                routes.add(Map.of(
                        "match", Map.of("prefix", ep.trim()),
                        "route", Map.of("cluster", deploymentName)
                ));
            }
        }
        // Default catch‑all route
        routes.add(Map.of(
                "match", Map.of("prefix", "/"),
                "route", Map.of("cluster", backendName)
        ));

        // --- Build Listener ---
        Map<String, Object> listener = Map.of(
                "name", "listener_http",
                "address", Map.of(
                        "socket_address", Map.of(
                                "address", "0.0.0.0",
                                "port_value", ENVOY_PORT
                        )
                ),
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

        // --- Wrap into ConfigMap object ---
        Map<String, Object> configMap = Map.of(
                "apiVersion", "v1",
                "kind", "ConfigMap",
                "metadata", Map.of(
                        "name", "envoy-config-" + backendName,
                        "namespace", PROXY_NAMESPACE
                ),
                "data", Map.of(
                        "envoy.yaml", yaml.dump(Map.of(
                                "static_resources", Map.of(
                                        "clusters", clusters,
                                        "listeners", List.of(listener)
                                )
                        ))
                )
        );

        // Write out to a temp file
        Path tmp = Files.createTempFile("envoy-config-" + backendName, ".yml");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            yaml.dump(configMap, w);
        }
        return tmp;
    }


    private void injectEnvoySidecar(Path yamlFile,
                                    String cfgMapName,
                                    String envoyImage,
                                    String targetNS)
            throws IOException, InterruptedException {
        LoaderOptions lo = new LoaderOptions();
        Yaml y = new Yaml(new SafeConstructor(lo));
        Map<String, Object> original;
        try (InputStream in = Files.newInputStream(yamlFile)) {
            original = y.load(in);
        }
        // Inject container and volume as before...
        DumperOptions dumpOpts = new DumperOptions();
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml output = new Yaml(dumpOpts);
        Path updated = Files.createTempFile("deployment-with-envoy", ".yml");
        try (Writer w = Files.newBufferedWriter(updated)) {
            output.dump(original, w);
        }
        new ProcessBuilder("kubectl", "apply", "-f", updated.toString(), "-n", targetNS)
                .inheritIO().start().waitFor();
    }

    private void patchServicePorts(String serviceName,
                                   String targetNS,
                                   int envoyTargetPort,
                                   int backendPort) throws IOException, InterruptedException {
        Path orig = Files.createTempFile("svc-" + serviceName, ".yaml");
        KubernetesUtil.getServiceYamlToFile(serviceName, targetNS, orig);
        LoaderOptions lo = new LoaderOptions();
        Yaml y = new Yaml(new SafeConstructor(lo));
        Map<String, Object> data;
        try (InputStream in = Files.newInputStream(orig)) {
            data = y.load(in);
        }
        Map<String, Object> spec = (Map<String, Object>) data.get("spec");
        List<Map<String, Object>> ports = new ArrayList<>();
        Map<String, Object> envoyPort = new LinkedHashMap<>();
        envoyPort.put("name", "http-cache");
        envoyPort.put("protocol", "TCP");
        envoyPort.put("port", 8089);
        envoyPort.put("targetPort", envoyTargetPort);
        ports.add(envoyPort);
        Map<String, Object> backendP = new LinkedHashMap<>();
        backendP.put("name", "http-backend");
        backendP.put("protocol", "TCP");
        backendP.put("port", 8092);
        backendP.put("targetPort", backendPort);
        ports.add(backendP);
        spec.put("ports", ports);
        DumperOptions dumpOpts = new DumperOptions();
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml out = new Yaml(new Representer(dumpOpts), dumpOpts);
        Path patched = Files.createTempFile("patched-svc-" + serviceName, ".yaml");
        try (Writer w = Files.newBufferedWriter(patched)) {
            out.dump(data, w);
        }
        KubernetesUtil.applyYaml(patched.toString(), targetNS);
    }
}
