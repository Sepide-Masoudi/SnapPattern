package com.example.design_pattern_prototyping.controller;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesUtil;
import com.example.design_pattern_prototyping.pattern_generator.*;
import com.example.design_pattern_prototyping.util.UILogger;
import com.example.design_pattern_prototyping.util.YamlEditor;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

public class PatternController {

    private static final Logger logger = Logger.getLogger(PatternController.class.getName());
    private UILogger uiLogger;

    @FXML private TextArea logTextArea;
    @FXML private  Label statusLabel;
    @FXML private  VBox patternFieldsBox;
    @FXML private  ComboBox<String> languageComboBox;
    @FXML private  ComboBox<String> patternDropdown;
    @FXML private  VBox asyncRequestReplyFields;
    @FXML private  VBox gatewayOffloadingFields;
    @FXML private  VBox gatewayAggregationFields;
    @FXML private  VBox requestCollapsingFields;
    @FXML private  VBox cacheAsideFields;
    @FXML private  VBox circuitBreakerFields;

    // Asynch Request Reply Pattern
    public TextField async_serviceName;
    public TextField async_endpointPath;

    // Gateway Offloading
    public TextField go_serviceEndpoint;
    public TextField go_serviceName;
    public TextField go_servicePort;

    // Cache Aside Pattern
    public TextField cachedEndpoints;
    public TextField backendService;

    // Gateway Aggregation Pattern
    public TextField ga_serviceName;
    public TextField ga_serviceEndpoint;
    public TextField ga_serviceHost;
    public TextField ga_servicePort;

    // Request Collapsing Pattern
    public TextField rc_backendService;

    //Circuit Breaker Pattern
    public TextField cb_serviceName;
    public TextField cb_max_pending_requests;
    public TextField cb_max_connections;
    public TextField cb_failureThreshold;
    public TextField cb_retry_attempts;

    private File yamlFile;
    private File instrumentedYamlFile;
    private Map<String, String> languageMap;

    @FXML
    public void initialize() {
        // Register this controller with the mediator
        ControllerMediatorImpl.getInstance().registerPatternBuilderController(this);
        patternDropdown.setValue("Baseline");

        Logger logger = Logger.getLogger("PatternLogger");
        uiLogger = new UILogger(logTextArea, logger);
        logger.info("PatternBuilderController initialized.");
    }

    @FXML
    public void handleFileUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml", "*.yml"));
        yamlFile = fileChooser.showOpenDialog(new Stage());
        if (yamlFile != null) {
            logger.info("YAML file selected: " + yamlFile.getAbsolutePath());
            uiLogger.info("YAML file selected: " + yamlFile.getAbsolutePath());
            statusLabel.setText("YAML file selected: " + yamlFile.getName());
        } else {
            statusLabel.setText("No YAML file selected.");
            uiLogger.warning("No YAML file selected.");
        }
    }

    @FXML
    public void openInstrumentationModal() {
        if (yamlFile == null) {
            showAlert(Alert.AlertType.WARNING, "Missing YAML", "Please upload a YAML configuration file first.");
            return;
        }

        try {
            String content = Files.readString(yamlFile.toPath());
            List<String> deploymentNames = YamlEditor.extractDeploymentNames(content);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/design_pattern_prototyping/DeploymentController.fxml"));
            Parent root = loader.load();
            DeploymentController controller = loader.getController();
            controller.setDeployments(deploymentNames);
            controller.setOriginalYamlFile(yamlFile);

            Stage modal = new Stage();
            modal.setTitle("Set Instrumentation Language");
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setScene(new Scene(root));
            modal.showAndWait();

            File instrumented = controller.getInstrumentedamlFile();
            if (instrumented != null) {
                this.instrumentedYamlFile = instrumented;
                this.languageMap = controller.getConfirmedLanguageMap();
            }

        } catch (Exception e) {
            String errorMsg = "Failed to process YAML file. Please check its structure and indentation.\n" +
                    "Error: " + e.getMessage();

            logger.log(Level.SEVERE, "YAML parsing failed", e);
            uiLogger.error(errorMsg);
            showAlert(Alert.AlertType.ERROR, "YAML Error", errorMsg);
        }
    }

    @FXML
    public void deleteApplication() {
        logger.info("User requested to delete the user application...");
        uiLogger.info("User requested to delete the user application...");
        statusLabel.setText("Deleting user application...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesUtil.startMinikube();
                if (minikubeStarted) {
                    KubernetesUtil.deleteUserNamespace();
                    KubernetesUtil.deletePatternNamespace();
                    logger.info("User application services deleted successfully...");
                    uiLogger.info("User application services deleted successfully...");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("user application deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    uiLogger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during user application deletion", e);
                uiLogger.error("Error during user application deletion: " + e.getMessage());
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during user application deletion."));
            }
        }).start();
    }

    @FXML
    public void deletePattern() {
        logger.info("User requested to delete the pattern...");
        uiLogger.info("User requested to delete the pattern...");
        statusLabel.setText("Deleting current pattern...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesUtil.startMinikube();
                if (minikubeStarted) {
                    KubernetesUtil.deletePatternNamespace();
                    uiLogger.info("Pattern deleted successfully.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Pattern deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    uiLogger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during pattern deletion", e);
                uiLogger.error("Error during pattern deletion: " + e.getMessage());
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during pattern deletion."));
            }
        }).start();
    }

    @FXML
    public void deployApplication() {
        File fileToDeploy = (instrumentedYamlFile != null) ? instrumentedYamlFile : yamlFile;

        if (fileToDeploy != null) {
            logger.info("User requested to deploy the application with configuration: " + fileToDeploy.getAbsolutePath());
            uiLogger.info("Deploying application with configuration: " + fileToDeploy.getAbsolutePath());
            statusLabel.setText("Deploying application configuration...");

            new Thread(() -> {
                try {
                    boolean minikubeStarted = KubernetesUtil.startMinikube();
                    if (minikubeStarted) {
                        KubernetesUtil.createNamespace("user");
                        KubernetesUtil.applyYaml(fileToDeploy.getAbsolutePath(), "user");
                        uiLogger.info("Application configuration applied.");
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Configuration applied successfully."));
                    } else {
                        logger.warning("Failed to start Minikube. Deployment aborted.");
                        uiLogger.warning("Failed to start Minikube. Deployment aborted.");
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deployment aborted."));
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during application deployment", e);
                    uiLogger.error("Error during application deployment: " + e.getMessage());
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during pattern deletion."));
                }
            }).start();
        } else {
            logger.warning("No YAML file selected. Deployment aborted.");
            uiLogger.warning("No YAML file selected. Deployment aborted.");
            showAlert(Alert.AlertType.WARNING, "Deploy Application", "Please select a YAML configuration file first.");
        }
    }

    @FXML
    public void handlePatternSelection() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User selected pattern: " + selectedPattern);
        uiLogger.info("User selected pattern: " + selectedPattern);

        patternFieldsBox.setVisible(!"Baseline".equals(selectedPattern));

        asyncRequestReplyFields.setVisible(false);
        gatewayOffloadingFields.setVisible(false);
        gatewayAggregationFields.setVisible(false);
        requestCollapsingFields.setVisible(false);
        cacheAsideFields.setVisible(false);
        circuitBreakerFields.setVisible(false);

        switch (selectedPattern) {
            case "Async Request Reply":
                asyncRequestReplyFields.setVisible(true);
                break;
            case "Gateway Offloading":
                gatewayOffloadingFields.setVisible(true);
                break;
            case "Gateway Aggregation":
                gatewayAggregationFields.setVisible(true);
                break;
            case "Request Collapsing":
                requestCollapsingFields.setVisible(true);
                break;
            case "Cache Aside":
                cacheAsideFields.setVisible(true);
                break;
            case "Circuit Breaker":
                circuitBreakerFields.setVisible(true);
                break;
            default:
                break;
        }
    }

    @FXML
    public void buildPattern() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User requested to build pattern: " + selectedPattern);
        uiLogger.info("User requested to build pattern: " + selectedPattern);
        statusLabel.setText("Deploying pattern: " + selectedPattern);

        Map<String, String> parameters = new HashMap<>();

        try {
            PatternGenerator generator = PatternGeneratorFactory.getGenerator(selectedPattern);
            String yamlFilePath = generator.getYamlFilePath();

            if ("Async Request Reply".equals(selectedPattern)) {
                parameters.put("SERVICE_NAME", async_serviceName.getText());
                parameters.put("ENDPOINT_PATH", async_endpointPath.getText());
            } else if ("Gateway Offloading".equals(selectedPattern)) {
                parameters.put("SERVICE_HOST", go_servicePort.getText());
                parameters.put("SERVICE_ENDPOINT", go_serviceEndpoint.getText());
                parameters.put("SERVICE_NAME", go_serviceName.getText());
            } else if ("Gateway Aggregation".equals(selectedPattern)) {
                parameters.put("SERVICE_1_NAME", ga_serviceName.getText());
                parameters.put("SERVICE_1_ENDPOINT", ga_serviceEndpoint.getText());
                parameters.put("SERVICE_1_HOST", ga_serviceHost.getText());
                parameters.put("SERVICE_1_PORT", ga_servicePort.getText());
            } else if ("Request Collapsing".equals(selectedPattern)) {
                parameters.put("BACKEND_SERVICE", rc_backendService.getText());
            } else if ("Cache Aside".equals(selectedPattern)) {
                parameters.put("CACHED_ENDPOINTS", cachedEndpoints.getText());
                parameters.put("BACKEND_SERVICE", backendService.getText());
            } else if ("Circuit Breaker".equals(selectedPattern)) {
                parameters.put("SERVICE_NAME", cb_serviceName.getText());
                parameters.put("MAX_PENDING_REQUESTS", cb_max_pending_requests.getText());
                parameters.put("MAX_CONNECTIONS", cb_max_connections.getText());
                parameters.put("FAILURE_THRESHOLD", cb_failureThreshold.getText());
                parameters.put("RETRY_ATTEMPTS", cb_retry_attempts.getText());
            }

            KubernetesUtil.createNamespace("pattern");
            generator.generatePattern(yamlFilePath, parameters);
            generator.deployPattern();

            logger.info("Pattern " + selectedPattern + " deployed successfully.");
            uiLogger.info("Pattern " + selectedPattern + " deployed successfully.");
            javafx.application.Platform.runLater(() -> statusLabel.setText("Pattern " + selectedPattern + " deployed successfully."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during pattern generation or deployment", e);
            uiLogger.error("Error during pattern generation or deployment");
            javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred while building the pattern."));
            showAlert(Alert.AlertType.ERROR, "Build Pattern", "An error occurred while building the pattern: " + e.getMessage());
        }
    }

    public String getSelectedPattern(){
        return patternDropdown.getValue();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
