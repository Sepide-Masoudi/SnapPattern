package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CircuitBreakerGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(CircuitBreakerGenerator.class.getName());
    private static final String NAMESPACE = "pattern";

    private String tempConfigPath;

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/CircuitBreaker/envoy/envoy-config.yml";
    }

    /* ---------------------------------------------------------------------- */
    /*  Pattern generation                                                    */
    /* ---------------------------------------------------------------------- */

    @Override
    public void generatePattern(String yamlFilePath, Map<String, String> parameters) {
        generatePattern(yamlFilePath, List.of(parameters));
    }

    @Override
    public void generatePattern(String templatePath, List<Map<String, String>> configs) {
        try {
            /* -------------------------------------------------------------- */
            /* 1. Build clusters & routes from user‑supplied configs          */
            /* -------------------------------------------------------------- */
            List<Map<String, Object>> clusters = new ArrayList<>();
            List<Map<String, Object>> routes   = new ArrayList<>();

            for (Map<String, String> cfg : configs) {
                String service       = cfg.get("SERVICE_NAME");
                String routePrefix   = cfg.get("ROUTE_PREFIX");
                String clusterName   = service + routePrefix.replace("/", "-");
                String dnsAddress    = service + ".user.svc.cluster.local";
                int    port          = Integer.parseInt(cfg.get("PORT"));

                /* ---- cluster ------------------------------------------------ */
                Map<String, Object> cluster = Map.of(
                        "name",             clusterName,
                        "connect_timeout",  "1s",
                        "type",             "STRICT_DNS",
                        "lb_policy",        "ROUND_ROBIN",
                        "circuit_breakers", Map.of(
                                "thresholds", List.of(Map.of(
                                        "priority",             "DEFAULT",
                                        "max_connections",      Integer.parseInt(cfg.get("MAX_CONNECTIONS")),
                                        "max_pending_requests", Integer.parseInt(cfg.get("MAX_PENDING_REQUESTS")),
                                        "max_requests",         Integer.parseInt(cfg.get("MAX_REQUESTS")),
                                        "max_retries",          Integer.parseInt(cfg.get("RETRY_ATTEMPTS"))
                                ))
                        ),
                        "outlier_detection", Map.of(
                                "consecutive_5xx",    Integer.parseInt(cfg.getOrDefault("FAILURE_THRESHOLD", "3")),
                                "interval",           "5s",
                                "base_ejection_time", "30s",
                                "max_ejection_percent", 50
                        ),
                        "load_assignment", Map.of(
                                "cluster_name", clusterName,
                                "endpoints", List.of(Map.of(
                                        "lb_endpoints", List.of(Map.of(
                                                "endpoint", Map.of(
                                                        "address", Map.of(
                                                                "socket_address", Map.of(
                                                                        "address",    dnsAddress,
                                                                        "port_value", port
                                                                )
                                                        )
                                                )
                                        ))
                                ))
                        )
                );

                /* ---- route -------------------------------------------------- */
                Map<String, Object> route = Map.of(
                        "match", Map.of("prefix", routePrefix),
                        "route", Map.of(
                                "cluster", clusterName,
                                "retry_policy", Map.of(
                                        "retry_on",       "gateway-error,connect-failure,refused-stream",
                                        "num_retries",    Integer.parseInt(cfg.get("RETRY_ATTEMPTS")),
                                        "per_try_timeout", cfg.get("PER_TRY_TIMEOUT")
                                )
                        )
                );

                clusters.add(cluster);
                routes.add(route);
            }

            /* -------------------------------------------------------------- */
            /* 2. Build listener with explicit typed_config for router filter */
            /* -------------------------------------------------------------- */
            Map<String, Object> routerFilter = Map.of(
                    "name", "envoy.filters.http.router",
                    "typed_config", Map.of(
                            "@type", "type.googleapis.com/envoy.extensions.filters.http.router.v3.Router"
                    )
            );

            Map<String, Object> hcmFilter = Map.of(
                    "name", "envoy.filters.network.http_connection_manager",
                    "typed_config", Map.of(
                            "@type", "type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager",
                            "stat_prefix", "ingress_http",
                            "codec_type",  "AUTO",
                            "route_config", Map.of(
                                    "name", "local_route",
                                    "virtual_hosts", List.of(Map.of(
                                            "name", "default-vh",
                                            "domains", List.of("*"),
                                            "routes", routes
                                    ))
                            ),
                            "http_filters", List.of(routerFilter)
                    )
            );

            Map<String, Object> listener = Map.of(
                    "name", "listener_http",
                    "address", Map.of(
                            "socket_address", Map.of(
                                    "address",    "0.0.0.0",
                                    "port_value", 8080
                            )
                    ),
                    "filter_chains", List.of(Map.of(
                            "filters", List.of(hcmFilter)
                    ))
            );

            Map<String, Object> envoyConfig = Map.of(
                    "static_resources", Map.of(
                            "clusters",  clusters,
                            "listeners", List.of(listener)
                    )
            );

            /* -------------------------------------------------------------- */
            /* 3. Dump to YAML & wrap in ConfigMap                           */
            /* -------------------------------------------------------------- */
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yaml = new Yaml(options);

            String envoyYaml = yaml.dump(envoyConfig);

            Map<String, Object> configMap = Map.of(
                    "apiVersion", "v1",
                    "kind",       "ConfigMap",
                    "metadata",   Map.of(
                            "name",      "envoy-circuitbreaker-config",
                            "namespace", NAMESPACE
                    ),
                    "data", Map.of("envoy.yaml", envoyYaml)
            );

            Path tempFile = Files.createTempFile("envoy-circuitbreaker-configmap", ".yml");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                yaml.dump(configMap, writer);
            }

            this.tempConfigPath = tempFile.toString();
            logger.info("Generated Envoy ConfigMap at: " + tempConfigPath);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to generate Envoy config", e);
        }
    }

    /* ---------------------------------------------------------------------- */
    /*  Pattern deployment                                                    */
    /* ---------------------------------------------------------------------- */

    @Override
    public void deployPattern() {
        try {
            if (tempConfigPath == null || tempConfigPath.isBlank()) {
                throw new IOException("Temporary ConfigMap file path does not exist.");
            }

            // Apply generated ConfigMap & static deployment YAML
            KubernetesUtil.applyYaml(tempConfigPath, NAMESPACE);
            KubernetesUtil.applyYaml(
                    "src/main/resources/Patterns/CircuitBreaker/envoy/envoy-circuitbreaker.yml",
                    NAMESPACE
            );

            logger.info("Circuit Breaker pattern deployed successfully.");

            // Clean up temp file
            Files.deleteIfExists(Path.of(tempConfigPath));
            logger.info("Temporary file deleted: " + tempConfigPath);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error deploying Circuit Breaker pattern.", e);
        }
    }
}
