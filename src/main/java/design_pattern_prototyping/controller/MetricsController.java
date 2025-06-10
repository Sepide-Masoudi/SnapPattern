package design_pattern_prototyping.controller;

import design_pattern_prototyping.Monitoring.*;
import design_pattern_prototyping.util.UILogger;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsController {

    private static final Logger logger = Logger.getLogger(MetricsController.class.getName());
    public UILogger uiLogger;

    @FXML private TextArea logTextArea;
    @FXML public Button exposeServicesButton;
    @FXML public Button getMetricsButton;
    @FXML public Button viewPlotsButton;
    @FXML public Button deployMetricsButton;
    @FXML public VBox metricsSetupBox;
    @FXML public Label statusLabel;
    @FXML public TextField patternTextField;
    private DeployMonitoringStack deployMonitoringStack;
    private QueryMetrics queryMetrics;
    private MetricsExporter metricsExporter;
    //private JaegerClient jaegerClient;

    @FXML
    public void initialize() {
        // Register this controller with the mediator
        ControllerMediatorImpl.getInstance().registerMetricsController(this);
        this.queryMetrics = new QueryMetrics();
        this.deployMonitoringStack = new DeployMonitoringStack();
        this.metricsExporter = new MetricsExporter();
        //this.jaegerClient = new JaegerClient();

        Logger logger = Logger.getLogger("MetricsLogger");
        uiLogger = new UILogger(logTextArea, logger);
        deployMonitoringStack.setLogger(uiLogger);
        queryMetrics.setLogger(uiLogger);
        metricsExporter.setLogger(uiLogger);
        logger.info("MetricsController initialized.");
    }

    @FXML
    private void exposeMonitoringServices() {
        logger.info("Starting port forwarding for monitoring services...");
        //PrometheusClient.startPortForwarding();
        GrafanaClient.startPortForwarding();
        JaegerClient.startPortForwarding();
    }

    @FXML
    public void deployMetrics() {
        deployMetricsButton.setDisable(true); // Disable the button during setup
        statusLabel.setText("Deploying monitoring stack...");
        logger.info("Starting deployment of monitoring stack...");
        uiLogger.info("Starting deployment of monitoring stack...");

        new Thread(() -> {
            boolean success = false;
            try {
                success = deployMonitoringStack.deployMonitoringStack();
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during monitoring stack deployment", e);
                uiLogger.error("Unexpected error during monitoring stack deployment" + e.getMessage());
            }

            final boolean deploymentSuccess = success;
            javafx.application.Platform.runLater(() -> {
                if (deploymentSuccess) {
                    statusLabel.setText("Monitoring stack deployed successfully.");
                    metricsSetupBox.setVisible(true);
                    metricsSetupBox.setManaged(true);
                    logger.info("Monitoring stack deployed successfully.");
                    uiLogger.info("Monitoring stack deployed successfully.");

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

    // Query Metrics and Results File
    @FXML
    public void generateMetrics() {
        logger.info("Generating metrics...");
        uiLogger.info("Generating metrics...");
        new Thread(() -> {
            try {
                String energyTimeSeries = queryMetrics.queryEnergyTimeSeries();
                Map<String, String> metrics = queryMetrics.queryAllMetrics();

                if (metrics.containsKey("error")) {
                    logger.warning("Error generating metrics: " + metrics.get("error"));
                    uiLogger.warning("Error generating metrics: " + metrics.get("error"));
                } else {
                    logger.info("Metrics generated successfully");
                    uiLogger.info("Metrics generated successfully");

                    ControllerMediator mediator = ControllerMediatorImpl.getInstance();
                    String workloadLevel = mediator.getSelectedWorkloadLevel();
                    // Check if the text field has input
                    String patternInput = patternTextField.getText();
                    String pattern = (patternInput != null && !patternInput.trim().isEmpty())
                            ? patternInput.trim()
                            : mediator.getSelectedPattern();
                    metricsExporter.exportMetricsExcel(metrics, workloadLevel, pattern);
                    metricsExporter.exportEnergyTimeSeriesExcel(energyTimeSeries, workloadLevel, pattern);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error generating metrics", e);
                uiLogger.error("Error generating metrics: " + e.getMessage());
            }
        }).start();
    }

    // Generate Plots
    @FXML
    public void makePlots() {
        logger.info("Generating metrics plots...");
        uiLogger.info("Generating metrics plots...");
        new Thread(() -> metricsExporter.runMetricsService()).start();
    }

    @FXML
    private void viewPlots() {
        logger.info("Opening plots...");
        uiLogger.info("Opening plots...");
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
            uiLogger.info("Plots loaded successfully. Total plots: " + plotFiles.length);
        } else {
            Label noPlotsLabel = new Label("No plots found in the results folder.");
            plotLayout.getChildren().add(noPlotsLabel);
            logger.warning("No plot files were found in results folder.");
            uiLogger.warning("No plot files were found in results folder.");
            showAlert("No Plots Found", "There are no plot files in the results folder. Please generate metrics to view plots.", Alert.AlertType.INFORMATION);
        }

        ScrollPane scrollPane = new ScrollPane(plotLayout);
        scrollPane.setFitToWidth(true);
        Scene plotScene = new Scene(scrollPane, 800, 600);
        plotViewerStage.setScene(plotScene);
        plotViewerStage.show();
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
