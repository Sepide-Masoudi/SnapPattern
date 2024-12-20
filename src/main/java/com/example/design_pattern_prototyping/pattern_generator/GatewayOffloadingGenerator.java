package com.example.design_pattern_prototyping.pattern_generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class GatewayOffloadingGenerator implements PatternGenerator {

    @Override
    public void generatePattern(String filePath, Map<String, String> parameters) {
        try {
            // Load the ingress YAML template
            Path ingressPath = Paths.get(filePath);
            String yamlContent = new String(Files.readAllBytes(ingressPath));

            // Replace placeholders with user-specified parameters
            yamlContent = yamlContent.replace("${SERVICE-HOST}", parameters.get("SERVICE_HOST"));
            yamlContent = yamlContent.replace("${SERVICE-ENDPOINT}", parameters.get("SERVICE_ENDPOINT"));
            yamlContent = yamlContent.replace("${SERVICE-NAME}", parameters.get("SERVICE_NAME"));

            // Write the updated content back to the file
            Files.write(ingressPath, yamlContent.getBytes());
            System.out.println("Gateway Offloading pattern generated successfully at " + filePath);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error generating Gateway Offloading pattern: " + e.getMessage());
        }
    }

    public void deployPattern() {
        try {
            // Step 1: Add Helm repositories and update
            Process addNginxRepo = new ProcessBuilder("helm", "repo", "add", "ingress-nginx", "https://kubernetes.github.io/ingress-nginx")
                    .inheritIO()
                    .start();
            addNginxRepo.waitFor();

            Process updateHelmRepos = new ProcessBuilder("helm", "repo", "update")
                    .inheritIO()
                    .start();
            updateHelmRepos.waitFor();

            // Step 2: Create namespaces
            Process createPatternNamespace = new ProcessBuilder("kubectl", "create", "namespace", "pattern").inheritIO().start();
            createPatternNamespace.waitFor();

            Process createProxyNamespace = new ProcessBuilder("kubectl", "create", "namespace", "proxy").inheritIO().start();
            createProxyNamespace.waitFor();

            // Step 3: Deploy NGINX Ingress Controller
            Process deployNginxIngress = new ProcessBuilder(
                    "helm", "install", "nginx-ingress", "ingress-nginx/ingress-nginx", "--namespace", "pattern")
                    .inheritIO()
                    .start();
            deployNginxIngress.waitFor();

            // Step 4: Apply the generated Gateway Offloading YAML
            Process applyIngress = new ProcessBuilder("kubectl", "apply", "-f", "nginx-ingress.yml", "--namespace", "pattern")
                    .inheritIO()
                    .start();
            applyIngress.waitFor();

            System.out.println("Gateway Offloading Pattern setup completed successfully.");

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error executing build steps for Gateway Offloading Pattern: " + e.getMessage());
        }
    }
}
