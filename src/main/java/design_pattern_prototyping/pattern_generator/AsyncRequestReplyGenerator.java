package design_pattern_prototyping.pattern_generator;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import java.util.logging.Level;
import java.util.logging.Logger;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

public class AsyncRequestReplyGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(AsyncRequestReplyGenerator.class.getName());
    private List<String> tempListenerPaths = new ArrayList<>();
    private String tempIngressPath;
    private static final String INGRESS_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/kong-ingress.yml";
    private static final String LISTENER_TEMPLATE = "src/main/resources/Patterns/AsyncRequestReply/listener/listener-deployment.yml";
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/AsyncRequestReply/kong-ingress.yml";
    }

    @Override
    public void generatePattern(String yamlFilePath, Map<String, String> parameters) {
        List<Map<String, String>> singleConfig = new ArrayList<>();
        singleConfig.add(parameters);
        generatePattern(yamlFilePath, singleConfig);
    }

    @Override
    public void generatePattern(String filePath, List<Map<String, String>> configs) {
        try {
            // --- Build Docker images once
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/proxy/Dockerfile.proxy", "proxy-service:local");
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/listener/Dockerfile.listener", "listener-service:local");
            loadImageMinikube("proxy-service:local");
            loadImageMinikube("listener-service:local");

            // --- Load base Ingress YAML and add paths dynamically
            InputStream ingressInput = new FileInputStream(INGRESS_TEMPLATE);
            Yaml yaml = new Yaml();
            Map<String, Object> ingressMap = yaml.load(ingressInput);

            List<Map<String, Object>> paths = new ArrayList<>();
            for (Map<String, String> entry : configs) {
                String path = entry.get("ENDPOINT_PATH");
                String normalizedPath = path.startsWith("/") ? path : "/" + path;

                Map<String, Object> pathEntry = Map.of(
                        "path", normalizedPath,
                        "pathType", "Prefix",
                        "backend", Map.of("service", Map.of("name", "proxy-service", "port", Map.of("number", 80)))
                );
                paths.add(pathEntry);

                // --- Generate listener YAML for this service
                String serviceName = entry.get("SERVICE_NAME");
                String fullUrl = "http://" + serviceName + ".user.svc.cluster.local/" + path;
                String listenerName = serviceName + "-listener";
                String listenerYaml = Files.readString(Paths.get(LISTENER_TEMPLATE))
                        .replace("${SERVICE_NAME}", fullUrl)
                        .replace("${LISTENER_NAME}", listenerName);
                Path tempListener = Files.createTempFile("listener-" + serviceName, ".yml");
                Files.writeString(tempListener, listenerYaml);
                tempListenerPaths.add(tempListener.toString());
            }

            // Inject paths into ingress
            Map<String, Object> rules = (Map<String, Object>) ((List<?>) ((Map<?, ?>) ingressMap.get("spec")).get("rules")).get(0);
            ((Map<String, Object>) rules.get("http")).put("paths", paths);

            // Write ingress YAML
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            Yaml dumper = new Yaml(options);
            Path tempIngressFile = Files.createTempFile("kong-ingress", ".yml");
            try (Writer writer = Files.newBufferedWriter(tempIngressFile)) {
                dumper.dump(ingressMap, writer);
            }
            tempIngressPath = tempIngressFile.toString();

        } catch (Exception e) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Pattern generation failed", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            executeCommand("helm", "repo", "add", "kong", "https://charts.konghq.com");
            executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            executeCommand("helm", "repo", "update");

            executeCommand("helm", "upgrade", "--install", "rabbitmq", "bitnami/rabbitmq",
                    "--set", "auth.username=user,auth.password=bitnami", "--namespace", NAMESPACE);

            KubernetesUtil.applyYaml("src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-deployment.yml", NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-service.yml", NAMESPACE);

            for (String listenerPath : tempListenerPaths) {
                KubernetesUtil.applyYaml(listenerPath, NAMESPACE);
            }

            executeCommand("helm", "upgrade", "--install", "kong", "kong/kong",
                    "--set", "ingressController.installCRDs=false", "--namespace", NAMESPACE);
            KubernetesUtil.applyYaml(tempIngressPath, NAMESPACE);

            for (String listenerPath : tempListenerPaths) Files.deleteIfExists(Paths.get(listenerPath));
            Files.deleteIfExists(Paths.get(tempIngressPath));

        } catch (Exception e) {
            Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Pattern deployment failed", e);
        }
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        logger.info("Running command: " + String.join(" ", command));
        Process process = new ProcessBuilder(command).inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            logger.info("Command succeeded: " + String.join(" ", command));
        } else {
            logger.warning("Command failed: " + String.join(" ", command));
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