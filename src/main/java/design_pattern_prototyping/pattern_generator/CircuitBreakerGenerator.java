package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CircuitBreakerGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CircuitBreakerGenerator.class.getName());
    private String tempConfigPath;
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/CircuitBreaker/envoy/envoy-config.yml";
    }

    @Override
    public void generatePattern(String yamlFilePath, Map<String, String> parameters) {
        List<Map<String, String>> singleConfig = new ArrayList<>();
        singleConfig.add(parameters);
        generatePattern(yamlFilePath, singleConfig);
    }

    @Override
    public void generatePattern(String templatePath, List<Map<String, String>> configs) {
        try {
            List<Map<String, Object>> clusters = new ArrayList<>();
            List<Map<String, Object>> routes = new ArrayList<>();

            for (Map<String, String> cfg : configs) {
                String service = cfg.get("SERVICE_NAME");
                String cluster = service + cfg.get("ROUTE_PREFIX").replace("/", "-");
                String address = service + ".user.svc.cluster.local";

                Map<String, Object> clusterDef = Map.of(
                        "name", cluster,
                        "connect_timeout", "1s",
                        "type", "STRICT_DNS",
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
                                                                        "address", address,
                                                                        "port_value", Integer.parseInt(cfg.get("PORT"))
                                                                )
                                                        )
                                                )
                                        ))
                                ))
                        )
                );

                Map<String, Object> route = Map.of(
                        "match", Map.of("prefix", cfg.get("ROUTE_PREFIX")),
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
            }

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
                                            "http_filters", List.of(Map.of("name", "envoy.filters.http.router"))
                                    )
                            ))
                    ))
            );

            Map<String, Object> finalConfig = Map.of("static_resources", Map.of(
                    "clusters", clusters,
                    "listeners", List.of(listener)
            ));

            // Dump envoy.yaml into a ConfigMap YAML
            DumperOptions opts = new DumperOptions();
            opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            opts.setPrettyFlow(true);
            Yaml yaml = new Yaml(opts);

            String envoyYaml = yaml.dump(finalConfig);
            Map<String, Object> configMap = Map.of(
                    "apiVersion", "v1",
                    "kind", "ConfigMap",
                    "metadata", Map.of("name", "envoy-circuitbreaker-config", "namespace", NAMESPACE),
                    "data", Map.of("envoy.yaml", envoyYaml)
            );

            Path tempFile = Files.createTempFile("envoy-circuitbreaker-configmap", ".yml");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                yaml.dump(configMap, writer);
            }

            this.tempConfigPath = tempFile.toString();
            logger.info("Generated envoy ConfigMap with all service configs: " + tempConfigPath);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to generate envoy config", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            if (tempConfigPath == null || tempConfigPath.isEmpty()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Step 3: Apply the generated Circuit Breaker YAML
            KubernetesUtil.applyYaml(tempConfigPath, NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/Patterns/CircuitBreaker/envoy/envoy-circuitbreaker.yml", NAMESPACE);

            logger.info("Circuit Breaker and retry pattern setup completed successfully.");

            // Delete temporary file
            Files.deleteIfExists(Paths.get(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error executing build steps for Circuit Breaker Pattern.", e);
        }
    }
}