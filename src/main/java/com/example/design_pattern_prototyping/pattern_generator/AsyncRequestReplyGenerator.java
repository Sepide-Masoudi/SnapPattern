package com.example.design_pattern_prototyping.pattern_generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class AsyncRequestReplyGenerator implements PatternGenerator {

    @Override
    public String getYamlFilePath() {
        return "src/main/resources/patterns/GatewayOffloading/ingress.yml";
    }

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            // Load ingress YAML content as a string
            Path path = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(path));
            // Load listener YAML content as a string
            Path listenerPath = Paths.get("src", "main", "resources", "patterns", "AsyncRequestReply", "listener", "listener_deployment.yml");
            String listenerContent = new String(Files.readAllBytes(listenerPath));

            String sendinghost = parameters.get("SEND_SERVICE_NAME") + ".user.svc.cluster.local";
            String receivinghost = parameters.get("RECEIVE_SERVICE_NAME") + ".user.svc.cluster.local";
            String receivingUrl = "http://"+receivinghost+"/"+parameters.get("SERVICE_ENDPOINT");

            // Replace placeholders with parameter values
            yamlContent = yamlContent.replace("${SERVICE_HOST}", sendinghost);
            yamlContent = yamlContent.replace("${SERVICE_ENDPOINT}", parameters.get("SERVICE_ENDPOINT"));
            yamlContent = yamlContent.replace("${SERVICE_PORT}", parameters.get("SERVICE_PORT"));
            listenerContent = listenerContent.replace("${SERVICE_URL}", receivingUrl);
            // Write the updated content back to the  File
            Files.write(path, yamlContent.getBytes());
            Files.write(listenerPath, listenerContent.getBytes());
            System.out.println("Async Request Reply pattern generated successfully at " + filePath);

            // Build Docker images for proxy and listener
            buildDockerImage("src/main/resources/Dockerfile.proxy", "proxy-service:local");
            buildDockerImage("src/main/resources/Dockerfile.listener", "listener-service:local");
            loadImageMinikube("proxy-service:local");
            loadImageMinikube("listener-service:local");

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error generating Async Request Reply pattern: " + e.getMessage());
        }
    }

    private void buildDockerImage(String dockerfilePath, String imageName) {
        try {
            Process dockerBuild = new ProcessBuilder(
                    "docker", "build", "-t", imageName, "-f", dockerfilePath, ".")
                    .inheritIO() // Display output in console
                    .start();

            int exitCode = dockerBuild.waitFor();
            if (exitCode == 0) {
                System.out.println("Docker image built successfully: " + imageName);
            } else {
                System.err.println("Failed to build Docker image: " + imageName);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error building Docker image: " + imageName);
        }
    }
    private void loadImageMinikube(String imageName) {
        try {
            Process loadImage = new ProcessBuilder(
                    "minikube", "image", "load", imageName)
                    .inheritIO() // Display output in console
                    .start();

            int exitCode = loadImage.waitFor();
            if (exitCode == 0) {
                System.out.println("Image loaded into Minikube successfully: " + imageName);
            } else {
                System.err.println("Failed to load image into Minikube: " + imageName);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error loading image into Minikube: " + imageName);
        }
    }

    @Override
    public void deployPattern() {
        try {
            // Step 1: Add Helm repositories and update them
            Process addKongRepo = new ProcessBuilder("helm", "repo", "add", "kong", "https://charts.konghq.com").inheritIO().start();
            addKongRepo.waitFor();

            Process addBitnamiRepo = new ProcessBuilder("helm", "repo", "add", "bitnami", "https://charts.bitnami.com/bitnami").inheritIO().start();
            addBitnamiRepo.waitFor();

            Process updateHelmRepos = new ProcessBuilder("helm", "repo", "update").inheritIO().start();
            updateHelmRepos.waitFor();

            // Step 2: Create namespaces
            Process createRabbitmqNamespace = new ProcessBuilder("kubectl", "create", "namespace", "rabbitmq").inheritIO().start();
            createRabbitmqNamespace.waitFor();

            Process createPatternNamespace = new ProcessBuilder("kubectl", "create", "namespace", "pattern").inheritIO().start();
            createPatternNamespace.waitFor();

            Process createProxyNamespace = new ProcessBuilder("kubectl", "create", "namespace", "proxy").inheritIO().start();
            createProxyNamespace.waitFor();

            // Step 3: Install RabbitMQ
            Process installRabbitmq = new ProcessBuilder("helm", "install", "rabbitmq", "bitnami/rabbitmq", "--set", "auth.username=user,auth.password=bitnami", "--namespace", "rabbitmq").inheritIO().start();
            installRabbitmq.waitFor();

            // Step 4: Install Kong
            Process installKong = new ProcessBuilder("helm", "install", "kong/kong", "--generate-name", "--set", "ingressController.installCRDs=false", "--namespace", "pattern").inheritIO().start();
            installKong.waitFor();

            // Step 5: Apply Kubernetes YAML files
            Process applyKongIngress = new ProcessBuilder("kubectl", "apply", "-f", "kong-nginx-ingress.yml", "--namespace", "pattern").inheritIO().start();
            applyKongIngress.waitFor();

            Process applyListener = new ProcessBuilder("kubectl", "apply", "-f", "listener-deployment.yml", "--namespace", "pattern").inheritIO().start();
            applyListener.waitFor();

            Process applyProxyDeployment = new ProcessBuilder("kubectl", "apply", "-f", "proxy-deployment.yml", "--namespace", "proxy").inheritIO().start();
            applyProxyDeployment.waitFor();

            Process applyProxyService = new ProcessBuilder("kubectl", "apply", "-f", "proxy-service.yml", "--namespace", "proxy").inheritIO().start();
            applyProxyService.waitFor();

            System.out.println("Async Request Reply Pattern setup completed successfully.");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error executing build steps for Async Request Reply Pattern: " + e.getMessage());
        }
    }
}
