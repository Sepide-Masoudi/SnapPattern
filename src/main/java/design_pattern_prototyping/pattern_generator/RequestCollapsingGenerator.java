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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestCollapsingGenerator implements PatternGenerator {
    private static final Logger logger = Logger.getLogger(RequestCollapsingGenerator.class.getName());
    private static final String NAMESPACE = "pattern";
    private static final String ENVOY_IMAGE = "envoyproxy/envoy:v1.30-latest";
    private static final int ENVOY_PORT = 8081;

    private static final String COLLAPSER_CONFIG_TEMPLATE = "src/main/resources/Patterns/RequestCollapsing/collapser/collapser-config.yml";
    private static final String COLLAPSER_DEPLOYMENT_TEMPLATE = "src/main/resources/Patterns/RequestCollapsing/collapser/collapser-deployment.yml";
    private static final String PROXY_DEPLOYMENT_TEMPLATE = "src/main/resources/Patterns/RequestCollapsing/batchEndpoint/batch-service-deployment-template.yml";

    Path tempEnvoyConfig = null;
    Path tempEnvoyDeployment = null;
    Path tempEnvoyService = null;
    Path tempCollapserDeployment = null;
    Path tempCollapserConfig = null;
    Path tempBatchDeployment = null;

    @Override
    public void generatePattern(Map<String, String> parameters) {
        try {
            String serviceName = parameters.get("SERVICE_NAME");
            String servicePort = parameters.get("SERVICE_PORT");
            String path = parameters.get("ENDPOINT_PATH");
            String deploymentName = "request-collapser";

            // --------- Batch Processor Image Generation ---------
            buildDockerImage("src/main/resources/Patterns/RequestCollapsing/batchEndpoint/Dockerfile", "batch-proxy-service:local");
            loadImageMinikube("batch-proxy-service:local");


            // Fetch and inject Envoy sidecar
            Path deployPath = Paths.get("deploy-" + serviceName + ".yaml");
            KubernetesUtil.getDeploymentYamlToFile(serviceName, "user", deployPath);
            injectEnvoySidecar(deployPath, "envoy-config-" + serviceName, ENVOY_IMAGE);

            // --------- Envoy ConfigMap Generation ---------
            tempEnvoyConfig = generateEnvoyConfigMap(serviceName, servicePort, path, deploymentName);
            logger.info("Temporary Envoy ConfigMap YAML generated at: " + tempEnvoyConfig);

            // Patch the service targetPort to envoy port ENVOY_PORT
            patchServicePortToEnvoy(serviceName, ENVOY_PORT);

            // --------- Collapser Deployment Generation ---------
            logger.info("Loading deployment template: " + COLLAPSER_DEPLOYMENT_TEMPLATE);
            Path deploymentPath = Paths.get(COLLAPSER_DEPLOYMENT_TEMPLATE);
            String deploymentYaml = new String(Files.readAllBytes(deploymentPath));

            deploymentYaml = deploymentYaml
                    .replace("${QUERY_PARAM}", parameters.get("QUERY_PARAM"))
                    .replace("${ID_FIELD}", parameters.get("ID_FIELD"));

            tempCollapserDeployment = Files.createTempFile("collapser-deployment-", ".yml");
            Files.write(tempCollapserDeployment, deploymentYaml.getBytes());
            logger.info("Temporary collapser deployment YAML generated at: " + tempCollapserDeployment);

            // --------- Collapser ConfigMap Generation ---------
            logger.info("Loading configmap template: " + COLLAPSER_CONFIG_TEMPLATE);
            Path configPath = Paths.get(COLLAPSER_CONFIG_TEMPLATE);
            String configYaml = new String(Files.readAllBytes(configPath));

            configYaml = configYaml.replace("${COLLAPSER_PATH}", parameters.get("COLLAPSER_PATH"));

            tempCollapserConfig = Files.createTempFile("collapser-configmap-", ".yml");
            Files.write(tempCollapserConfig, configYaml.getBytes());
            logger.info("Temporary collapser configmap YAML generated at: " + tempCollapserConfig);

            // --------- Batch Service Deployment + Service Generation ---------
            logger.info("Loading deployment template: " + COLLAPSER_DEPLOYMENT_TEMPLATE);
            Path proxyDeploymentPath = Paths.get(PROXY_DEPLOYMENT_TEMPLATE);
            String batchTemplate = new String(Files.readAllBytes(proxyDeploymentPath));

            batchTemplate = batchTemplate
                    .replace("${DB_HOST}", parameters.get("DB_HOST"))
                    .replace("${DB_PORT}", parameters.get("DB_PORT"))
                    .replace("${DB_NAME}", parameters.get("DB_NAME"))
                    .replace("${DB_USER}", parameters.get("DB_USER"))
                    .replace("${DB_PASS}", parameters.get("DB_PASS"))
                    .replace("${BATCH_QUERY}", parameters.get("BATCH_QUERY"));

            tempBatchDeployment = Files.createTempFile("batch-service-deployment-", ".yml");
            Files.writeString(tempBatchDeployment, batchTemplate);
            logger.info("Temporary batch service deployment YAML generated at: " + tempBatchDeployment);


        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating collapser or envoy YAML files.", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            // Apply YAMLs using their string paths
            KubernetesUtil.applyYaml(tempEnvoyConfig.toString());
            KubernetesUtil.applyYaml(tempEnvoyService.toString());
            KubernetesUtil.applyYaml(tempEnvoyDeployment.toString());
            KubernetesUtil.applyYaml(tempCollapserConfig.toString(), NAMESPACE);
            KubernetesUtil.applyYaml(tempCollapserDeployment.toString(), NAMESPACE);
            KubernetesUtil.applyYaml(tempBatchDeployment.toString(), NAMESPACE);

            // Cleanup temp files
            Files.deleteIfExists(tempEnvoyConfig);
            Files.deleteIfExists(tempEnvoyService);
            Files.deleteIfExists(tempEnvoyDeployment);
            Files.deleteIfExists(tempCollapserConfig);
            Files.deleteIfExists(tempCollapserDeployment);
            Files.deleteIfExists(tempBatchDeployment);

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

    private Path generateEnvoyConfigMap(String backendName, String backendPort, String collapserPath, String deploymentName) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);

        List<Map<String, Object>> clusters = new ArrayList<>();
        List<Map<String, Object>> routes = new ArrayList<>();

        // Collapser cluster
        String collapserClusterName = deploymentName + collapserPath.replace("/", "-");
        String collapserService = deploymentName + ".pattern.svc.cluster.local";

        Map<String, Object> collapserCluster = Map.of(
                "name", collapserClusterName,
                "connect_timeout", "1s",
                "type", "STRICT_DNS",
                "lb_policy", "ROUND_ROBIN",
                "load_assignment", Map.of(
                        "cluster_name", collapserClusterName,
                        "endpoints", List.of(Map.of(
                                "lb_endpoints", List.of(Map.of(
                                        "endpoint", Map.of(
                                                "address", Map.of(
                                                        "socket_address", Map.of(
                                                                "address", collapserService,
                                                                "port_value", 80
                                                        )
                                                )
                                        )
                                ))
                        ))
                )
        );

        Map<String, Object> collapserRoute = Map.of(
                "match", Map.of("prefix", collapserPath),
                "route", Map.of("cluster", collapserClusterName)
        );

        clusters.add(collapserCluster);
        routes.add(collapserRoute);


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

        // Listener
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
        String namespace = metadata.containsKey("namespace") ? (String) metadata.get("namespace") : "default";

        new ProcessBuilder("kubectl", "delete", "deployment", name, "-n", namespace).inheritIO().start().waitFor();
        new ProcessBuilder("kubectl", "apply", "-f", updatedFile.toAbsolutePath().toString()).inheritIO().start().waitFor();

        System.out.println("Envoy sidecar injected and deployment applied: " + name);
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
                NAMESPACE,
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
}