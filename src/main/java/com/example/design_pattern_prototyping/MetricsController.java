package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Monitoring.DeployMonitoringStack;
import com.example.design_pattern_prototyping.Monitoring.QueryMetrics;
import com.example.design_pattern_prototyping.Monitoring.MetricsVisualizer;
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
// TODO Add UI Alerts for Deployment status and metrics retrieval like in workloads Tab

public class MetricsController {

    private static final String NAMESPACE = "user|pattern";
    private static final Logger logger = Logger.getLogger(MetricsController.class.getName());

    @FXML public Button getMetricsButton;
    @FXML public Button viewPlotsButton;
    @FXML private VBox metricsSetupBox;
    @FXML private Label statusLabel;
    @FXML private Button deployMetricsButton;

    private final DeployMonitoringStack deployMonitoringStack;
    private QueryMetrics queryMetrics;

    public MetricsController() {
        this.deployMonitoringStack = new DeployMonitoringStack();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing MetricsController...");
        this.queryMetrics = new QueryMetrics();
    }

    //TODO If Monitoring Stack Deployment failed statusLabel/Logs should return this.
    public void deployMetrics() {
        deployMetricsButton.setDisable(true); // Disable the button during setup
        statusLabel.setText("Setting up monitoring stack...");
        logger.info("Starting deployment of monitoring stack...");

        // Run setup in a background thread
        new Thread(() -> {
            try {
                deployMonitoringStack.deployMonitoringStack();
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Monitoring stack set up successfully.");
                    metricsSetupBox.setVisible(true);
                    metricsSetupBox.setManaged(true);
                    logger.info("Monitoring stack deployed successfully.");
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error setting up monitoring stack", e);
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error setting up monitoring stack."));
            } finally {
                javafx.application.Platform.runLater(() -> deployMetricsButton.setDisable(false));
            }
        }).start();
    }

    //Query Metrics and Generate Plots and Results File
    @FXML
    private void generateMetrics() {
        logger.info("Generating metrics...");
        new Thread(() -> {
            try {
                Map<String, String> metrics = queryMetrics.queryAllMetrics(NAMESPACE);

                if (metrics.containsKey("error")) {
                    logger.warning("Error fetching metrics: " + metrics.get("error"));
                } else {
                    logger.info("Metrics fetched successfully: " + metrics);
                    MetricsVisualizer.sendMetricsToPython(metrics);
                }
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

        File folder = new File("../../../../../Python/results");
        if (folder.exists() && folder.isDirectory()) {
            File[] plotFiles = folder.listFiles((dir, name) -> name.endsWith(".png"));
            if (plotFiles != null && plotFiles.length > 0) {
                for (File plotFile : plotFiles) {
                    Image image = new Image(plotFile.toURI().toString());
                    ImageView imageView = new ImageView(image);
                    imageView.setFitWidth(600);
                    imageView.setPreserveRatio(true);
                    plotLayout.getChildren().add(imageView);
                }
                logger.info("Plots loaded successfully.");
            } else {
                Label noPlotsLabel = new Label("No plots found in the results folder.");
                plotLayout.getChildren().add(noPlotsLabel);
                logger.warning("No plots found in the results folder.");
            }
        } else {
            Label errorLabel = new Label("Results folder not found.");
            plotLayout.getChildren().add(errorLabel);
            logger.severe("Results folder not found.");
        }

        Scene plotScene = new Scene(plotLayout, 800, 600);
        plotViewerStage.setScene(plotScene);
        plotViewerStage.show();
    }
}