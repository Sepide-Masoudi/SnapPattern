package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Monitoring.*;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsController {

    private static final String USER_NAMESPACE = "user";
    private static final String PATTERN_NAMESPACE = "pattern";
    private static final Logger logger = Logger.getLogger(MetricsController.class.getName());

    @FXML public Button exposeServicesButton;
    @FXML public Button getMetricsButton;
    @FXML public Button viewPlotsButton;
    @FXML public Button deployMetricsButton;
    @FXML public VBox metricsSetupBox;
    @FXML public Label statusLabel;
    @FXML public Button loadDashboard;
    private DeployMonitoringStack deployMonitoringStack;
    private QueryMetrics queryMetrics;
    private JaegerClient jaegerClient;

    @FXML
    public void initialize() {
        // Register this controller with the mediator
        ControllerMediatorImpl.getInstance().registerMetricsController(this);
        this.queryMetrics = new QueryMetrics();
        this.deployMonitoringStack = new DeployMonitoringStack();
        this.jaegerClient = new JaegerClient();
        logger.info("MetricsController initialized.");
    }

    @FXML
    private void exposeMonitoringServices() {
        logger.info("Starting port forwarding for monitoring services...");
        PrometheusClient.startPortForwarding();
        GrafanaClient.startPortForwarding();
        JaegerClient.startPortForwarding();
    }

    @FXML
    public void deployMetrics() {
        deployMetricsButton.setDisable(true); // Disable the button during setup
        statusLabel.setText("Deploying monitoring stack...");
        logger.info("Starting deployment of monitoring stack...");

        new Thread(() -> {
            boolean success = false;
            try {
                success = deployMonitoringStack.deployMonitoringStack();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during monitoring stack deployment", e);
            }

            final boolean deploymentSuccess = success;
            javafx.application.Platform.runLater(() -> {
                if (deploymentSuccess) {
                    statusLabel.setText("Monitoring stack deployed successfully.");
                    metricsSetupBox.setVisible(true);
                    metricsSetupBox.setManaged(true);
                    logger.info("Monitoring stack deployed successfully.");

                    // Start port forwarding for Prometheus and Grafana
                    //PrometheusClient.startPortForwarding();
                    //GrafanaClient.startPortForwarding();
                    //JaegerClient.startPortForwarding();
                } else {
                    statusLabel.setText("Error deploying monitoring stack.");
                }
                deployMetricsButton.setDisable(false);
            });
        }).start();
    }

    // Query Metrics and Generate Plots and Results File
    @FXML
    private void generateMetrics() {
        logger.info("Generating metrics...");
        new Thread(() -> {
            try {
                Map<String, String> metrics = queryMetrics.queryAllMetrics();

                if (metrics.containsKey("error")) {
                    logger.warning("Error fetching metrics: " + metrics.get("error"));
                } else {
                    logger.info("Metrics fetched successfully: " + metrics);

                    ControllerMediator mediator = ControllerMediatorImpl.getInstance();
                    String workloadLevel = mediator.getSelectedWorkloadLevel();
                    String pattern = mediator.getSelectedPattern();
                    MetricsVisualizer.exportMetricsExcel(metrics, workloadLevel, pattern);

                    String excelFilePath = "Python/results/metrics.xlsx";
                    MetricsVisualizer.sendMetricsToPython(excelFilePath);
                }
                // Collect trace data for the "user" namespace
                logger.info("Collecting trace data for namespace: " + USER_NAMESPACE);
                jaegerClient.collectTraceData(USER_NAMESPACE, "Python/results/user-trace-data.json");

                // Collect trace data for the "pattern" namespace
                logger.info("Collecting trace data for namespace: " + PATTERN_NAMESPACE);
                jaegerClient.collectTraceData(PATTERN_NAMESPACE, "Python/results/pattern-trace-data.json");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error generating metrics", e);
            }
        }).start();
    }

    @FXML
    private void viewPlots() {
        logger.info("Viewing plots...");
        Stage plotViewerStage = new Stage();
        plotViewerStage.setTitle("Metric Plots");

        VBox plotLayout = new VBox(10);
        plotLayout.setStyle("-fx-padding: 10; -fx-alignment: center; -fx-background-color: #f0f0f0;");

        File folder = new File("Python/results");

        File[] plotFiles = folder.listFiles((dir, name) -> name.endsWith(".png"));

        if (plotFiles != null && plotFiles.length > 0) {
            for (File plotFile : plotFiles) {
                Image image = new Image(plotFile.toURI().toString());
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(600);
                imageView.setPreserveRatio(true);
                plotLayout.getChildren().add(imageView);
            }
            logger.info("Plots loaded successfully. Total plots: " + plotFiles.length);
        } else {
            Label noPlotsLabel = new Label("No plots found in the results folder.");
            plotLayout.getChildren().add(noPlotsLabel);
            logger.warning("No plot files were found in the 'Python/results' folder.");
            showAlert("No Plots Found", "There are no plot files in the results folder. Please generate metrics to view plots.", Alert.AlertType.INFORMATION);
        }

        ScrollPane scrollPane = new ScrollPane(plotLayout);
        scrollPane.setFitToWidth(true);
        Scene plotScene = new Scene(scrollPane, 800, 600);
        plotViewerStage.setScene(plotScene);
        plotViewerStage.show();
    }

    @FXML
    private void loadGrafanaDashboard() {
        logger.info("Attempting to load Grafana dashboard...");
        if (GrafanaClient.isPortForwardingActive()) {
            try {
                javafx.application.Platform.runLater(() -> {
                    Stage dashboardStage = new Stage();
                    new GrafanaClient().start(dashboardStage);
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load Grafana dashboard", e);
                showAlert("Error", "Unable to open Grafana Dashboard. Please make sure port forwarding is active and try again.", Alert.AlertType.ERROR);
            }
        } else {
            logger.warning("Port forwarding is not active. Please restart port forwarding.");
            showAlert("Port Forwarding Inactive", "Port forwarding is not active. Please restart port forwarding and try again.", Alert.AlertType.WARNING);
        }
    }

    private void showAlert(String title, String message, Alert.AlertType alertType) {
        System.out.println("Showing alert - " + title + ": " + message);
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
