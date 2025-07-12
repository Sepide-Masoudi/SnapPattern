package design_pattern_prototyping.controller;

import design_pattern_prototyping.Monitoring.*;
import design_pattern_prototyping.util.UILogger;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsController {

    private static final Logger logger = Logger.getLogger(MetricsController.class.getName());
    public UILogger uiLogger;

    @FXML private TextArea logTextArea;
    @FXML public Button exportMetricsButton;
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

        File folder = new File("Python/results/plots");
        File[] plotFiles = folder.listFiles((dir, name) -> name.endsWith(".png"));

        if (plotFiles == null || plotFiles.length == 0) {
            showAlert("No Plots Found", "There are no plot files in the results folder. Please generate metrics to view plots.", Alert.AlertType.INFORMATION);
            logger.warning("No plot files were found in results folder.");
            uiLogger.warning("No plot files were found in results folder.");
            return;
        }

        // Group by type (basic heuristic)
        Map<String, List<File>> groupedPlots = new HashMap<>();
        for (File file : plotFiles) {
            String key = file.getName().toLowerCase().contains("boxplot") ? "Boxplots" :
                    file.getName().toLowerCase().contains("timeseries") ? "Time Series" : "Other";
            groupedPlots.computeIfAbsent(key, k -> new ArrayList<>()).add(file);
        }

        TabPane tabPane = new TabPane();

        for (Map.Entry<String, List<File>> entry : groupedPlots.entrySet()) {
            String category = entry.getKey();
            List<File> files = entry.getValue();
            files.sort(Comparator.comparing(File::getName));

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(15);
            grid.setPadding(new Insets(15));
            int cols = 2;
            for (int i = 0; i < files.size(); i++) {
                File plotFile = files.get(i);
                Image image = new Image(plotFile.toURI().toString());
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(700);
                imageView.setPreserveRatio(true);

                Tooltip tooltip = new Tooltip(plotFile.getName());
                Tooltip.install(imageView, tooltip);

                imageView.setOnMouseClicked(event -> openImageInModal(plotFile));

                int row = i / cols;
                int col = i % cols;
                grid.add(imageView, col, row);
            }

            ScrollPane scrollPane = new ScrollPane(grid);
            scrollPane.setFitToWidth(true);
            Tab tab = new Tab(category, scrollPane);
            tabPane.getTabs().add(tab);
        }

        Scene scene = new Scene(tabPane, 1400, 1000);
        plotViewerStage.setScene(scene);
        plotViewerStage.show();

        logger.info("Plots loaded and displayed in grouped view.");
        uiLogger.info("Plots loaded and displayed in grouped view.");
    }

    private void openImageInModal(File imageFile) {
        Stage modalStage = new Stage();
        modalStage.initModality(Modality.APPLICATION_MODAL);
        modalStage.setTitle("Plot Viewer: " + imageFile.getName());

        Image image = new Image(imageFile.toURI().toString());
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(1200);

        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);

        Scene scene = new Scene(scrollPane, 1400, 1000);
        modalStage.setScene(scene);
        modalStage.show();
    }

    @FXML
    private void exportMetrics() {
        File sourceFile = new File("Python/results/metrics_data.xlsx");

        if (!sourceFile.exists()) {
            showAlert("Export Metrics", "Metrics file not found. Please run the metrics analysis first.", Alert.AlertType.ERROR);
            logger.warning("metrics_data.xlsx not found at expected path.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Metrics Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fileChooser.setInitialFileName("metrics_data.xlsx");

        File destinationFile = fileChooser.showSaveDialog(exportMetricsButton.getScene().getWindow());
        if (destinationFile != null) {
            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("Export Metrics", "Metrics exported successfully to:\n" + destinationFile.getAbsolutePath(), Alert.AlertType.INFORMATION);
                logger.info("Metrics exported to: " + destinationFile.getAbsolutePath());
            } catch (IOException e) {
                showAlert("Export Metrics", "Failed to export metrics:\n" + e.getMessage(), Alert.AlertType.ERROR);
                logger.log(Level.SEVERE, "Failed to export metrics", e);
            }
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
