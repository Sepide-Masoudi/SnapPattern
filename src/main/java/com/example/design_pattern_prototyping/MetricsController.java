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
// TODO Add UI Alerts for Deployment status and metrics retrieval like in workloads Tab

public class MetricsController {

    private static final String NAMESPACE = "user|pattern";
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
        // Initialize QueryMetrics and MetricsVisualizer
        this.queryMetrics = new QueryMetrics();
    }

    //TODO If Monitoring Stack Deployment failed statusLabel should return this.
    public void deployMetrics() {
        deployMetricsButton.setDisable(true); // Disable the button during setup
        statusLabel.setText("Setting up monitoring stack...");

        // Run setup in a background thread
        new Thread(() -> {
            try {
                deployMonitoringStack.deployMonitoringStack();

                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Monitoring stack set up successfully.");
                    metricsSetupBox.setVisible(true);
                    metricsSetupBox.setManaged(true);
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error setting up monitoring stack."));
                e.printStackTrace();
            } finally {
                javafx.application.Platform.runLater(() -> deployMetricsButton.setDisable(false));
            }
        }).start();
    }
    //Query Metrics and Generate Plots and Results File

    @FXML
    private void generateMetrics() {
        new Thread(() -> {
            try {
                Map<String, String> metrics = queryMetrics.queryAllMetrics(NAMESPACE);

                if (metrics.containsKey("error")) {
                    System.err.println("Error fetching metrics: " + metrics.get("error"));
                } else {
                    System.out.println("Sending metrics to MetricsService: " + metrics);
                    MetricsVisualizer.sendMetricsToPython(metrics);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void viewPlots() {
        // Create a new stage for the plot viewer
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
                    imageView.setFitWidth(600); // Scale the width
                    imageView.setPreserveRatio(true);
                    plotLayout.getChildren().add(imageView);
                }
            } else {
                Label noPlotsLabel = new Label("No plots found in the results folder.");
                plotLayout.getChildren().add(noPlotsLabel);
            }
        } else {
            Label errorLabel = new Label("Results folder not found.");
            plotLayout.getChildren().add(errorLabel);
        }

        Scene plotScene = new Scene(plotLayout, 800, 600);
        plotViewerStage.setScene(plotScene);
        plotViewerStage.show();
    }
}
