package design_pattern_prototyping.Monitoring;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import design_pattern_prototyping.util.UILogger;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DeployMonitoringStack {

    private static final String OTEL_NAMESPACE = "otel";
    private static final String MONITOR_NAMESPACE = "monitoring";
    private static final Logger logger = Logger.getLogger(DeployMonitoringStack.class.getName());
    private UILogger uiLogger;

    public void setLogger(UILogger logger) {
        this.uiLogger = logger;
    }

    public boolean deployMonitoringStack() {
        try {
            logger.info("Adding Helm repositories...");
            uiLogger.info("Adding Helm repositories...");
            KubernetesUtil.executeCommand("helm", "repo", "add", "prometheus-community", "https://prometheus-community.github.io/helm-charts");
            KubernetesUtil.executeCommand("helm", "repo", "add", "kepler", "https://sustainable-computing-io.github.io/kepler-helm-chart");
            KubernetesUtil.executeCommand("helm", "repo", "add", "grafana", "https://grafana.github.io/helm-charts");

            logger.info("Updating Helm repositories...");
            uiLogger.info("Updating Helm repositories...");
            KubernetesUtil.executeCommand("helm", "repo", "update");

            logger.info("Creating namespaces...");
            uiLogger.info("Creating namespaces...");
            KubernetesUtil.createNamespace(OTEL_NAMESPACE);
            KubernetesUtil.createNamespace(MONITOR_NAMESPACE);

            createConfigMap();

            logger.info("Installing Cert-Manager...");
            uiLogger.info("Installing Cert-Manager...");
            KubernetesUtil.applyYaml("https://github.com/cert-manager/cert-manager/releases/download/v1.18.0/cert-manager.yaml", "cert-manager");

            waitForDeploymentReady("cert-manager", "cert-manager");
            waitForDeploymentReady("cert-manager-cainjector", "cert-manager");
            waitForDeploymentReady("cert-manager-webhook", "cert-manager");

            Thread.sleep(15000);

            // Sleep briefly to ensure webhook and certs are established
            Thread.sleep(10000);

            logger.info("Installing OpenTelemetry Operator...");
            uiLogger.info("Installing OpenTelemetry Operator...");
            KubernetesUtil.applyYaml("https://github.com/open-telemetry/opentelemetry-operator/releases/latest/download/opentelemetry-operator.yaml", "opentelemetry-operator-system");

            waitForDeploymentReady("opentelemetry-operator-controller-manager", "opentelemetry-operator-system");
            KubernetesUtil.applyYaml("src/main/resources/monitoring/otel/otel-operator-instrumentation.yml", OTEL_NAMESPACE);

            logger.info("Installing OpenTelemetry Collector...");
            uiLogger.info("Installing OpenTelemetry Collector...");
            KubernetesUtil.applyYaml("src/main/resources/monitoring/otel/otel-collector-config.yml", OTEL_NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/monitoring/otel/otel-collector-rbac.yml",OTEL_NAMESPACE);
            KubernetesUtil.applyYaml("src/main/resources/monitoring/otel/otel-collector-deployment.yml", OTEL_NAMESPACE);
            KubernetesUtil.executeCommand("kubectl", "rollout", "restart", "deployment", "-n", "user");

            logger.info("Installing Prometheus...");
            uiLogger.info("Installing Prometheus...");
            KubernetesUtil.executeCommand("helm", "upgrade", "-install", "prometheus",
                    "prometheus-community/kube-prometheus-stack",
                    "--namespace", MONITOR_NAMESPACE,
                    "-f", "src/main/resources/monitoring/prometheus-values.yml");

            logger.info("Installing Kepler...");
            uiLogger.info("Installing Kepler...");
            KubernetesUtil.executeCommand("helm", "upgrade", "-install", "kepler", "kepler/kepler",
                    "-f", "src/main/resources/monitoring/kepler-values.yml",
                    "--namespace", MONITOR_NAMESPACE,
                    "--set", "securityContext.privileged=true",
                    "--set", "serviceMonitor.enabled=true",
                    "--set", "serviceMonitor.labels.release=prometheus");

            logger.info("Monitoring stack deployed successfully.");
            uiLogger.info("Monitoring stack deployed successfully.");
            return true;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error deploying the monitoring stack", e);
            uiLogger.error("Error deploying the monitoring stack: " + e.getMessage());
            return false;
        }
    }

    private void createConfigMap() throws IOException, InterruptedException {
        logger.info("Checking if ConfigMap already exists...");
        uiLogger.info("Checking if ConfigMap already exists...");
        try {
            KubernetesUtil.executeCommand("kubectl", "delete", "configmap", "grafana-dashboard-config", "-n", MONITOR_NAMESPACE);
            logger.info("ConfigMap deleted. Recreating it...");
            uiLogger.info("ConfigMap deleted. Recreating it...");
        } catch (IOException e) {
            logger.info("ConfigMap does not exist. Proceeding to create it...");
            uiLogger.info("ConfigMap does not exist. Proceeding to create it...");
        }

        KubernetesUtil.executeCommand("kubectl", "create", "configmap", "grafana-dashboard-config", "-n", MONITOR_NAMESPACE,
                "--from-file=src/main/resources/monitoring/Kepler-Exporter.json");

        logger.info("Labeling the ConfigMap as a Grafana dashboard...");
        uiLogger.info("Labeling the ConfigMap as a Grafana dashboard...");
        KubernetesUtil.executeCommand("kubectl", "label", "configmap", "grafana-dashboard-config", "-n", MONITOR_NAMESPACE, "grafana_dashboard=1", "--overwrite");
    }

    private void waitForDeploymentReady(String deploymentName, String namespace) throws IOException, InterruptedException {
        logger.info("Waiting for deployment '" + deploymentName + "' in namespace '" + namespace + "' to be ready...");
        uiLogger.info("Waiting for deployment '" + deploymentName + "' in namespace '" + namespace + "' to be ready...");
        KubernetesUtil.executeCommand("kubectl", "wait",
                "--for=condition=Available",
                "--timeout=180s",
                "deployment/" + deploymentName,
                "-n", namespace);
        logger.info("Deployment '" + deploymentName + "' is ready.");
        uiLogger.info("Deployment '" + deploymentName + "' is ready.");
    }
}
