package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CircuitBreakerGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CircuitBreakerGenerator.class.getName());
    private static final String NAMESPACE = "user";
    private static final String ENVOY_IMAGE = "envoyproxy/envoy:v1.30-latest";
    private static final int ENVOY_PORT = 8081;

    private final List<Path> tempFiles = new ArrayList<>();

    @Override
    public void generatePattern(Map<String, String> parameters) {
        generatePattern(List.of(parameters));
    }

    @Override
    public void generatePattern(List<Map<String, String>> configs) {
        tempFiles.clear();
        try {
            // Group configs by service
            Map<String, List<Map<String, String>>> grouped = configs.stream()
                    .collect(Collectors.groupingBy(cfg -> cfg.get("SERVICE_NAME")));

            for (String service : grouped.keySet()) {
                List<Map<String, String>> serviceConfigs = grouped.get(service);
                String backendPort = serviceConfigs.get(0).get("PORT");

                // Fetch and inject Envoy sidecar
                Path deployPath = Paths.get("deploy-" + service + ".yaml");
                KubernetesUtil.getDeploymentYamlToFile(service, NAMESPACE, deployPath);
                injectEnvoySidecar(deployPath, "envoy-config-" + service, ENVOY_IMAGE);

                // Generate ConfigMap for Envoy with circuit breaker logic
                Path envoyConfig = generateEnvoyConfigMap(service, serviceConfigs);
                tempFiles.add(envoyConfig);
                logger.info("Generated ConfigMap for " + service + ": " + envoyConfig);

                // Patch the service targetPort to envoy port ENVOY_PORT
                patchServicePortToEnvoy(service, ENVOY_PORT);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to generate Circuit Breaker resources", e);
        }
    }

    @Override
    public void deployPattern() {
        for (Path file : tempFiles) {
            try {
                KubernetesUtil.applyYaml(file.toString());
                logger.info("Applied: " + file);
                Files.deleteIfExists(file);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to apply or delete: " + file, e);
            }
        }
    }

    private void patchServicePortToEnvoy(String serviceName, int envoyPort) throws IOException, InterruptedException {
        logger.info("Patching service " + serviceName + " targetPort to Envoy port " + envoyPort);

        // Build patch JSON for Kubernetes service
        String patchJson = "[{\"op\": \"replace\", \"path\": \"/spec/ports/0/targetPort\", \"value\": " + envoyPort + "}]";

        ProcessBuilder patchProcess = new ProcessBuilder(
                "kubectl",
                "patch",
                "service",
                serviceName,
                "-n",
                "user",
                "--type=json",
                "-p",
                patchJson
        );
        patchProcess.inheritIO();
        Process process = patchProcess.start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Successfully patched service " + serviceName);
        } else {
            logger.warning("Failed to patch service " + serviceName + ". Exit code: " + exitCode);
        }
    }

    private Path generateEnvoyConfigMap(String backendName, List<Map<String, String>> configs) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        List<Map<String, Object>> clusters = new ArrayList<>();
        List<Map<String, Object>> routes = new ArrayList<>();

        String defaultBackendService = backendName + "." + NAMESPACE + ".svc.cluster.local";
        int defaultPort = 8080;

        for (Map<String, String> cfg : configs) {
            String service = cfg.get("SERVICE_NAME");
            String routePrefix = cfg.get("ROUTE_PREFIX");
            String cluster = service + routePrefix.replace("/", "-");
            String address = service + "." + NAMESPACE + ".svc.cluster.local";

            Map<String, Object> clusterDef = Map.of(
                    "name", cluster,
                    "connect_timeout", "3s",
                    "type", "STATIC",
                    "lb_policy", "ROUND_ROBIN",
                    "circuit_breakers", Map.of(
                            "thresholds", List.of(Map.of(
                                    "priority", "DEFAULT",
                                    "max_connections", Integer.parseInt(cfg.get("MAX_CONNECTIONS")),
                                    "max_pending_requests", Integer.parseInt(cfg.get("MAX_PENDING_REQUESTS")),
                                    "max_requests", Integer.parseInt(cfg.get("MAX_REQUESTS")),
                                    "max_retries", Integer.parseInt(cfg.get("RETRY_ATTEMPTS"))
                            ))
                    ),
                    "outlier_detection", Map.of(
                            "consecutive_5xx", Integer.parseInt(cfg.getOrDefault("FAILURE_THRESHOLD", "3")),
                            "interval", "5s",
                            "base_ejection_time", "30s",
                            "max_ejection_percent", 50
                    ),
                    "load_assignment", Map.of(
                            "cluster_name", cluster,
                            "endpoints", List.of(Map.of(
                                    "lb_endpoints", List.of(Map.of(
                                            "endpoint", Map.of(
                                                    "address", Map.of(
                                                            "socket_address", Map.of(
                                                                    "address", "127.0.0.1",
                                                                    "port_value", Integer.parseInt(cfg.get("PORT"))
                                                            )
                                                    )
                                            )
                                    ))
                            ))
                    )
            );

            Map<String, Object> route = Map.of(
                    "match", Map.of("prefix", routePrefix),
                    "route", Map.of(
                            "cluster", cluster,
                            "retry_policy", Map.of(
                                    "retry_on", "gateway-error,connect-failure,refused-stream",
                                    "num_retries", Integer.parseInt(cfg.get("RETRY_ATTEMPTS")),
                                    "per_try_timeout", cfg.get("PER_TRY_TIMEOUT")
                            )
                    )
            );

            clusters.add(clusterDef);
            routes.add(route);

            defaultPort = Integer.parseInt(cfg.get("PORT"));
        }

        Map<String, Object> defaultCluster = Map.of(
                "name", backendName,
                "connect_timeout", "3s",
                "type", "STATIC",
                "lb_policy", "ROUND_ROBIN",
                "load_assignment", Map.of(
                        "cluster_name", backendName,
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", "127.0.0.1",
                                                                "port_value", defaultPort
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

        clusters.add(defaultCluster);
        routes.add(defaultRoute);

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
                                        "route_config", Map.of(
                                                "name", "local_route",
                                                "virtual_hosts", List.of(Map.of(
                                                        "name", "default-vh",
                                                        "domains", List.of("*"),
                                                        "routes", routes
                                                ))
                                        ),
                                        "http_filters", List.of(new LinkedHashMap<String, Object>() {{
                                            put("name", "envoy.filters.http.router");
                                            put("typed_config", Map.of(
                                                    "@type", "type.googleapis.com/envoy.extensions.filters.http.router.v3.Router"
                                            ));
                                        }})
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
                "metadata", Map.of("name", "envoy-config-" + backendName, "namespace", NAMESPACE),
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
}