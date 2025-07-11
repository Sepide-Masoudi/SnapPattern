package design_pattern_prototyping.pattern_generator;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GatewayOffloadingGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(GatewayOffloadingGenerator.class.getName());
<<<<<<< HEAD

    // Path to your static template in the repo
    private static final String TEMPLATE_PATH =
            "src/main/resources/Patterns/GatewayOffloading/nginx-ingress.yml";

    // Namespaces
    private static final String INGRESS_NS = "pattern";  // where ingress‑nginx lives
    private static final String USER_NS     = "user";    // where micro‑services live

    // NodePort you chose for Ingress
    private static final String NODE_PORT = "32342";

    // Holds the path to the temp YAML
    private String tempConfigPath;

    // -------------------------------------------------------------------------
    // PatternGenerator interface
    // -------------------------------------------------------------------------

    @Override
    public String getYamlFilePath() {
        return TEMPLATE_PATH;
    }

    @Override
    public void generatePattern(String ignored, Map<String, String> params) {
        try {
            logger.info(() -> "Loading template: " + TEMPLATE_PATH);
            Path template = Paths.get(TEMPLATE_PATH);
=======
    private String tempConfigPath = "src/main/resources/Patterns/GatewayOffloading/nginx-ingress.yml";
    private static final String NAMESPACE = "pattern";

    @Override
    public void generatePattern(Map<String, String> parameters) {
        try {
            logger.info("Loading template: " + tempConfigPath);
            Path templatePath = Paths.get(tempConfigPath);
            String yamlContent = new String(Files.readAllBytes(templatePath));
>>>>>>> 6d246dd86bd8744473a8666ed60ed311e98d9c38

            String yaml = Files.readString(template);

            // Replace placeholders
            yaml = yaml
                    .replace("${SERVICE_ENDPOINT}", params.getOrDefault("SERVICE_ENDPOINT", "/"))
                    .replace("${SERVICE_NAME}",     params.getOrDefault("SERVICE_NAME", "default-service"))
                    .replace("${SERVICE_PORT}",     params.getOrDefault("SERVICE_PORT", "8080"))
                    .replace("${USER_NAMESPACE}",   USER_NS);

            // Deterministic temp file path under /tmp
            Path tmp = Files.createTempFile(Paths.get("/tmp"), "gateway-offloading-config-", ".yml");
            Files.writeString(tmp, yaml);
            tempConfigPath = tmp.toString();

            logger.info("Generated YAML at: " + tempConfigPath);
            System.out.println("YAML path: " + tempConfigPath);
            Thread.sleep(5_000);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error generating Gateway Offloading YAML", e);
            tempConfigPath = null;
        }
    }

    @Override
    public void deployPattern() {
        if (tempConfigPath == null || !Files.exists(Paths.get(tempConfigPath))) {
            logger.severe("YAML file missing – aborting deployment.");
            return;
        }

        try {
            KubernetesUtil.createNamespace(INGRESS_NS);
            waitUntilNamespaceActive(INGRESS_NS);

            KubernetesUtil.exec("helm", "repo", "add", "ingress-nginx",
                    "https://kubernetes.github.io/ingress-nginx");
            KubernetesUtil.exec("helm", "repo", "update");

<<<<<<< HEAD
            KubernetesUtil.exec("helm", "upgrade", "--install", "nginx-ingress",
                    "ingress-nginx/ingress-nginx",
                    "--namespace", INGRESS_NS,
                    "--set", "controller.service.type=NodePort",
                    "--set", "controller.service.nodePorts.http=" + NODE_PORT,
                    "--set", "controller.admissionWebhooks.enabled=false");

            KubernetesUtil.applyYaml(tempConfigPath, USER_NS);
=======
            // Step 3: Deploy NGINX Ingress Controller
            executeCommand("helm", "upgrade", "-install", "nginx-ingress", "ingress-nginx/ingress-nginx", "--namespace", "pattern",
                    "--set", "controller.service.type=NodePort",
                    "--set", "controller.service.nodePorts.http=32342",
                    "--set", "controller.admissionWebhooks.enabled=false");

            // Step 4: Apply the generated Gateway Offloading YAML
            KubernetesUtil.applyYaml(tempConfigPath, "user");
>>>>>>> 6d246dd86bd8744473a8666ed60ed311e98d9c38

            logger.info("Gateway Offloading pattern deployed!");

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Deployment failed", e);
        } finally {
            // Comment this out while debugging if you want to keep the file
            // try { Files.deleteIfExists(Paths.get(tempConfigPath)); } catch (IOException ignored) {}
        }
    }

    /* Helper: wait until a namespace phase == Active */
    private void waitUntilNamespaceActive(String ns) throws IOException, InterruptedException {
        for (int i = 0; i < 30; i++) {
            String phase = KubernetesUtil.execAndCapture(
                    "kubectl", "get", "ns", ns, "-o", "jsonpath={.status.phase}").trim();
            if ("Active".equalsIgnoreCase(phase)) return;
            logger.info(() -> "Namespace '" + ns + "' phase=" + phase + " – waiting …");
            Thread.sleep(1_000);
        }
        throw new IOException("Namespace '" + ns + "' did not become Active in 30 s");
    }
}
