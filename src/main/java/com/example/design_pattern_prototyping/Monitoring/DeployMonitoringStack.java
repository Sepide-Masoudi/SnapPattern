package com.example.design_pattern_prototyping.Monitoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeployMonitoringStack {

    private static final Logger logger = Logger.getLogger(DeployMonitoringStack.class.getName());

    public boolean deployMonitoringStack() {
        try {
            // Step 1: Add Helm repositories
            logger.info("Adding Helm repositories...");
            executeCommand("helm", "repo", "add", "prometheus-community", "https://prometheus-community.github.io/helm-charts");
            executeCommand("helm", "repo", "add", "kepler", "https://sustainable-computing-io.github.io/kepler-helm-chart");
            executeCommand("helm", "repo", "add", "grafana", "https://grafana.github.io/helm-charts");
            //executeCommand("helm", "repo", "add", "istio", "https://istio-release.storage.googleapis.com/charts");
            //executeCommand("helm", "repo", "add", "jaegertracing", "https://jaegertracing.github.io/helm-charts");

            // Step 2: Update Helm repositories
            logger.info("Updating Helm repositories...");
            executeCommand("helm", "repo", "update");

            // Step 3: Create namespaces
            logger.info("Creating namespaces...");
            createNamespaceIfNotExists("otel");
            createNamespaceIfNotExists("monitoring");

            // Create Grafana ConfigMap
            createConfigMap();

            // Step 4: Install or upgrade tools
            logger.info("Installing OpenTelemetry stack...");
            logger.info("Installing Cert-Manager...");
            executeCommand("kubectl", "apply", "-f", "https://github.com/cert-manager/cert-manager/releases/download/v1.17.0/cert-manager.yaml");

            // Wait for Cert-Manager to be ready
            waitForDeploymentReady("cert-manager", "cert-manager");
            waitForDeploymentReady("cert-manager-cainjector", "cert-manager");
            waitForDeploymentReady("cert-manager-webhook", "cert-manager");

            logger.info("Installing OpenTelemetry Operator...");
            executeCommand("kubectl", "apply", "-f", "https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml");
            // Wait for OpenTelemetry Operator controller to be ready
            waitForDeploymentReady("opentelemetry-operator-controller-manager", "opentelemetry-operator-system");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/otel/otel-operator-instrumentation.yml");

            logger.info("Installing OpenTelemetry Collector...");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/otel/otel-collector-config.yml");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/otel/otel-collector-rbac.yml");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/otel/otel-collector-deployment.yml");
            executeCommand("kubectl", "rollout", "restart", "deployment", "-n", "user");

            logger.info("Installing Prometheus...");
            executeCommand("helm", "upgrade", "-install", "prometheus", "prometheus-community/kube-prometheus-stack", "--namespace", "monitoring", "-f", "src/main/resources/monitoring/prometheus-values.yml");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/ServiceMonitor.yml");
            executeCommand("kubectl", "apply", "-f", "src/main/resources/monitoring/PodMonitor.yml");

            logger.info("Installing Kepler...");
            executeCommand("helm", "upgrade", "-install", "kepler", "kepler/kepler",
                    "-f", "src/main/resources/monitoring/kepler-values.yml",
                    "--namespace", "monitoring",
                    "--set", "securityContext.privileged=true",
                    "--set", "serviceMonitor.enabled=true",
                    "--set", "serviceMonitor.labels.release=prometheus");
            /*
            logger.info("Installing Istio-base...");
            executeCommand("helm", "upgrade", "-install", "istio-base", "istio/base", "--namespace", "istio-system");

            logger.info("Installing Istiod...");
            executeCommand("helm", "upgrade", "-install", "istiod", "istio/istiod",
                    "--namespace", "istio-system",
                    "--set", "meshConfig.enableTracing=true",
                    "--set", "meshConfig.defaultConfig.tracing.sampling=100",
                    "--set", "meshConfig.defaultConfig.tracing.zipkin.address=jaeger-collector.monitoring.svc.cluster.local:9411",
                    "--set", "meshConfig.outboundTrafficPolicy.mode=ALLOW_ANY",
                    "--set", "telemetry.enabled=true",
                    "--set", "values.prometheus.enabled=true",
                    "--set", "values.global.proxy.tracer=jaeger",
                    "--set", "values.global.proxy.envoyStatsMatcher.includeAll=true");

            logger.info("Labeling namespace for Istio injection...");
            try {
                executeCommand("kubectl", "label", "namespace", "user", "istio-injection=enabled", "--overwrite");
                logger.info("Successfully labeled the 'user' namespace with istio-injection=enabled.");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to label the 'user' namespace. Ensure the namespace exists.", e);
            }

            logger.info("Installing Jaeger...");
            executeCommand("helm", "upgrade", "-install", "jaeger", "jaegertracing/jaeger",
                    "--namespace", "monitoring",
                    "-f", "src/main/resources/monitoring/jaeger-values.yaml");
            */
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
                executeCommand("kubectl", "create", "namespace", namespace);
            }
        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Error checking/creating namespace: " + namespace, e);
        }
    }

    private void createConfigMap() throws IOException, InterruptedException {
        logger.info("Checking if ConfigMap already exists...");
        try {
            executeCommand("kubectl", "delete", "configmap", "grafana-dashboard-config", "-n", "monitoring");
            logger.info("ConfigMap deleted. Recreating it...");
        } catch (IOException e) {
            logger.info("ConfigMap does not exist. Proceeding to create it...");
        }

        executeCommand("kubectl", "create", "configmap", "grafana-dashboard-config", "-n", "monitoring",
                "--from-file=src/main/resources/monitoring/Kepler-Exporter.json");

        logger.info("Labeling the ConfigMap as a Grafana dashboard...");
        executeCommand("kubectl", "label", "configmap", "grafana-dashboard-config", "-n", "monitoring", "grafana_dashboard=1", "--overwrite");
    }

    private void executeCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[stdout] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stdout of process", e);
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.warning("[stderr] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stderr of process", e);
            }
        }).start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }

    private void waitForDeploymentReady(String deploymentName, String namespace) throws IOException, InterruptedException {
        logger.info("Waiting for deployment '" + deploymentName + "' in namespace '" + namespace + "' to be ready...");
        executeCommand("kubectl", "wait",
                "--for=condition=Available",
                "--timeout=" + 180 + "s",
                "deployment/" + deploymentName,
                "-n", namespace);
        logger.info("Deployment '" + deploymentName + "' is ready.");
    }

}
