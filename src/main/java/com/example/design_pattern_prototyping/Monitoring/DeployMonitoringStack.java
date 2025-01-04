package com.example.design_pattern_prototyping.Monitoring;

import java.io.IOException;

public class DeployMonitoringStack {

    public void deployMonitoringStack() {
        try {
            // Step 1: Add Helm repositories
            System.out.println("Adding Helm repositories...");
            runCommand("helm", "repo", "add", "prometheus-community", "https://prometheus-community.github.io/helm-charts");
            runCommand("helm", "repo", "add", "kepler", "https://sustainable-computing-io.github.io/kepler-helm-chart");
            runCommand("helm", "repo", "add", "grafana", "https://grafana.github.io/helm-charts");
            runCommand("helm", "repo", "add", "istio", "https://istio-release.storage.googleapis.com/charts");
            runCommand("helm", "repo", "add", "jaegertracing", "https://jaegertracing.github.io/helm-charts");

            // Step 2: Update Helm repositories
            System.out.println("Updating Helm repositories...");
            runCommand("helm", "repo", "update");

            // Step 3: Create namespaces
            System.out.println("Creating namespaces...");
            runCommand("kubectl", "create", "namespace", "monitoring");
            runCommand("kubectl", "create", "namespace", "istio-system");

            // Step 4: Install or upgrade Tools
            System.out.println("Installing Prometheus...");
            runCommand("helm", "upgrade", "-install", "prometheus", "prometheus-community/kube-prometheus-stack", "--namespace", "monitoring");

            System.out.println("Installing Kepler...");
            runCommand("helm", "upgrade", "-install", "kepler", "kepler/kepler",
                    "--namespace", "monitoring",
                    "--set", "serviceMonitor.enabled=true",
                    "--set", "serviceMonitor.labels.release=prometheus");

            System.out.println("Installing Grafana...");
            runCommand("helm", "upgrade", "-install", "grafana", "grafana/grafana",
                    "--namespace", "monitoring",
                    "--set", "adminUser=admin",
                    "--set", "adminPassword=admin");

            System.out.println("Installing Istio-base...");
            runCommand("helm", "upgrade", "-install", "istio-base", "istio/base", "--namespace", "istio-system");

            System.out.println("Installing Istiod...");
            runCommand("helm", "upgrade", "-install", "istiod", "istio/istiod",
                    "--namespace", "istio-system",
                    "--set", "meshConfig.defaultConfig.tracing.sampling=100",
                    "--set", "meshConfig.defaultConfig.tracing.zipkin.address=jaeger:9411",
                    "--set", "telemetry.enabled=true",
                    "--set", "values.global.proxy.envoyStatsMatcher.includeAll=true");

            System.out.println("Labeling namespace for Istio injection...");
            runCommand("kubectl", "label", "namespace", "user", "istio-injection=enabled", "--overwrite");

            // TODO NOTE: How to fetch Endpints dynamically with Kubernetes Commands
            System.out.println("Installing Jaeger...");
            runCommand("helm", "install", "jaeger", "jaegertracing/jaeger",
                    "--namespace", "monitoring",
                    "--set", "agent.enabled=false",
                    "--set", "collector.enabled=true",
                    "--set", "query.enabled=true",
                    "--set", "ui.enabled=true");

            System.out.println("Monitoring stack deployed successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error deploying the monitoring stack: " + e.getMessage());
        }
    }

    private void runCommand(String... command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.inheritIO();
            int exitCode = processBuilder.start().waitFor();
            if (exitCode != 0) {
                System.out.println("Command failed: " + String.join(" ", command));
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Error executing command: " + String.join(" ", command) + " -> " + e.getMessage());
        }
    }
}
