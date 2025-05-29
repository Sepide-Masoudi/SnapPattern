package com.example.design_pattern_prototyping.pattern_generator;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesUtil;

public class AsyncRequestReplyGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(AsyncRequestReplyGenerator.class.getName());
    private String tempIngressPath;
    private String tempListenerPath;
    private static final String NAMESPACE = "pattern";

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/Patterns/AsyncRequestReply/kong-ingress.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            logger.info("Loading Kong Ingress template from: " + filePath);
            Path templatePath = Paths.get(filePath);
            String ingressContent = new String(Files.readAllBytes(templatePath));

            // Replace placeholder in ingress template
            ingressContent = ingressContent.replace("${ENDPOINT_PATH}", parameters.get("ENDPOINT_PATH"));

            // Write to a temporary file
            Path tempIngressFile = Files.createTempFile("kong-ingress-temp", ".yml");
            Files.write(tempIngressFile, ingressContent.getBytes());
            tempIngressPath = tempIngressFile.toString();
            logger.info("Generated Kong Ingress at temporary path: " + tempIngressPath);

            // Replace listener placeholder
            Path listenerPath = Paths.get("src/main/resources/Patterns/AsyncRequestReply/listener/listener-deployment.yml");
            String listenerContent = new String(Files.readAllBytes(listenerPath));

            String targetHost = parameters.get("SERVICE_NAME") + ".user.svc.cluster.local";
            String fullTargetUrl = "http://" + targetHost + "/" + parameters.get("ENDPOINT_PATH");
            listenerContent = listenerContent.replace("${SERVICE_NAME}", fullTargetUrl);
            logger.info("Updated listener deployment with SERVICE_URL: " + fullTargetUrl);

            // Write to a temporary file
            Path tempListenerFile = Files.createTempFile("listener-deployment-temp", ".yml");
            Files.write(tempListenerFile, listenerContent.getBytes());
            tempListenerPath = tempListenerFile.toString();
            logger.info("Generated Listener at temporary path: " + tempListenerPath);

            // Build Docker images
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/proxy/Dockerfile.proxy", "proxy-service:local");
            buildDockerImage("src/main/resources/Patterns/AsyncRequestReply/listener/Dockerfile.listener", "listener-service:local");
            loadImageMinikube("proxy-service:local");
            loadImageMinikube("listener-service:local");

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error generating Async Request Reply pattern.", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            if (tempIngressPath == null) {
                throw new IOException("No generated Kong Ingress file found.");
            }
            if (tempListenerPath == null) {
                throw new IOException("No generated Listener file found.");
            }

            // Helm repositories
            executeCommand("helm", "repo", "add", "kong", "https://charts.konghq.com");
            executeCommand("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami");
            executeCommand("helm", "repo", "update");

            // RabbitMQ
            executeCommand("helm", "upgrade", "--install", "rabbitmq", "bitnami/rabbitmq", "--set",
                    "auth.username=user,auth.password=bitnami", "--namespace", NAMESPACE);

            // Proxy
            KubernetesUtil.applyYaml("src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-deployment.yml", NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/Patterns/AsyncRequestReply/proxy/proxy-service.yml", NAMESPACE);

            // Listener
            KubernetesUtil.applyYaml(tempListenerPath, NAMESPACE);

            // Kong and Ingress
            executeCommand("helm", "upgrade", "--install", "kong", "kong/kong", "--set",
                    "ingressController.installCRDs=false", "--namespace", NAMESPACE);
            KubernetesUtil.applyYaml(tempIngressPath, NAMESPACE);

            logger.info("Async Request Reply Pattern setup completed successfully.");

            Files.deleteIfExists(Paths.get(tempIngressPath));
            logger.info("Temporary file deleted: " + tempIngressPath);
            Files.deleteIfExists(Paths.get(tempListenerPath));
            logger.info("Temporary file deleted: " + tempListenerPath);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error during Async Request Reply pattern deployment.", e);
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