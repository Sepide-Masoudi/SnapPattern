package com.example.design_pattern_prototyping.Monitoring;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeployMonitoringStack {

    private static final Logger logger = Logger.getLogger(DeployMonitoringStack.class.getName());

    public boolean deployMonitoringStack() {
        try {
            // Step 1: Add Helm repositories
            logger.info("Adding Helm repositories...");
            runCommand("helm", "repo", "add", "prometheus-community", "https://prometheus-community.github.io/helm-charts");
            runCommand("helm", "repo", "add", "kepler", "https://sustainable-computing-io.github.io/kepler-helm-chart");
            runCommand("helm", "repo", "add", "grafana", "https://grafana.github.io/helm-charts");
            runCommand("helm", "repo", "add", "istio", "https://istio-release.storage.googleapis.com/charts");
            runCommand("helm", "repo", "add", "jaegertracing", "https://jaegertracing.github.io/helm-charts");

            // Step 2: Update Helm repositories
            logger.info("Updating Helm repositories...");
            runCommand("helm", "repo", "update");

            // Step 3: Create namespaces
            logger.info("Creating namespaces...");
            createNamespaceIfNotExists("monitoring");
            createNamespaceIfNotExists("istio-system");

            // Create Grafana ConfigMap
            createConfigMap();

            // Step 4: Install or upgrade tools
            logger.info("Installing Prometheus...");
            runCommand("helm", "upgrade", "-install", "prometheus", "prometheus-community/kube-prometheus-stack", "--namespace", "monitoring", "-f", "src/main/resources/monitoring/istio-enabled-values.yml");
            runCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/ServiceMonitor.yml");
            runCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/PodMonitor.yml");

            logger.info("Installing Kepler...");
            runCommand("helm", "upgrade", "-install", "kepler", "kepler/kepler",
                    "-f", "src/main/resources/monitoring/kepler-values.yml",
                    "--namespace", "monitoring",
                    "--set", "securityContext.privileged=true",
                    "--set", "serviceMonitor.enabled=true",
                    "--set", "serviceMonitor.labels.release=prometheus");

            /**
             * logger.info("Installing Grafana...");
            runCommand("helm", "upgrade", "-install", "grafana", "grafana/grafana",
                    "--namespace", "monitoring",
                    "-f", "src/main/resources/monitoring/grafana-values.yml",
                    "--set", "adminUser=admin",
                    "--set", "adminPassword=admin");
             **/

            logger.info("Installing Istio-base...");
            runCommand("helm", "upgrade", "-install", "istio-base", "istio/base", "--namespace", "istio-system");

            logger.info("Installing Istiod...");
            runCommand("helm", "upgrade", "-install", "istiod", "istio/istiod",
                    "--namespace", "istio-system",
                    "--set", "meshConfig.enableTracing=true",
                    "--set", "meshConfig.defaultConfig.tracing.sampling=100",
                    "--set", "meshConfig.defaultConfig.tracing.zipkin.address=jaeger-collector.istio-system.svc.cluster.local:9411",
                    "--set", "meshConfig.outboundTrafficPolicy.mode=ALLOW_ANY",
                    "--set", "telemetry.enabled=true",
                    "--set", "values.prometheus.enabled=true",
                    "--set", "values.global.proxy.tracer=jaeger",
                    "--set", "values.global.proxy.envoyStatsMatcher.includeAll=true");

            logger.info("Labeling namespace for Istio injection...");
            try {
                runCommand("kubectl", "label", "namespace", "user", "istio-injection=enabled", "--overwrite");
                logger.info("Successfully labeled the 'user' namespace with istio-injection=enabled.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to label the 'user' namespace. Ensure the namespace exists.", e);
            }

            System.out.println("Installing Jaeger...");
            runCommand("helm", "upgrade", "-install", "jaeger", "jaegertracing/jaeger",
                    "--namespace", "monitoring",
                    "-f", "src/main/resources/monitoring/jaeger.yaml");

            logger.info("Monitoring stack deployed successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deploying the monitoring stack", e);
            return false;
        }
    }

    private void createNamespaceIfNotExists(String namespace) {
        try {
            // Check if the namespace already exists
            ProcessBuilder processBuilder = new ProcessBuilder("kubectl", "get", "namespace", namespace);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                logger.info("Namespace '" + namespace + "' already exists. Skipping creation.");
            } else {
                logger.info("Namespace '" + namespace + "' does not exist. Creating...");
                runCommand("kubectl", "create", "namespace", namespace);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error checking/creating namespace: " + namespace, e);
        }
    }

    private void createConfigMap() throws IOException, InterruptedException {
        logger.info("Checking if ConfigMap already exists...");
        try {
            runCommand("kubectl", "delete", "configmap", "grafana-dashboard-config", "-n", "monitoring");
            logger.info("ConfigMap deleted. Recreating it...");
        } catch (IOException e) {
            logger.info("ConfigMap does not exist. Proceeding to create it...");
        }

        runCommand("kubectl", "create", "configmap", "grafana-dashboard-config", "-n", "monitoring",
                "--from-file=src/main/resources/monitoring/Kepler-Exporter.json");

        logger.info("Labeling the ConfigMap as a Grafana dashboard...");
        runCommand("kubectl", "label", "configmap", "grafana-dashboard-config", "-n", "monitoring", "grafana_dashboard=1", "--overwrite");
    }

    private void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO();
        int exitCode = processBuilder.start().waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }
}
