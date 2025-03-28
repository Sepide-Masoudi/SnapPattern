package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesDeployer;
import com.example.design_pattern_prototyping.pattern_generator.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;

public class PatternBuilderController {

    private static final Logger logger = Logger.getLogger(PatternBuilderController.class.getName());

    public Label statusLabel;
    public VBox patternFieldsBox;
    public ComboBox<String> patternDropdown;
    public VBox asyncRequestReplyFields;
    public VBox gatewayOffloadingFields;
    public VBox gatewayAggregationFields;
    public VBox cacheAsideFields;
    public VBox circuitBreakerFields;

    // Asynch Request Reply Pattern
    public TextField sendingServiceName;
    public TextField sendingServiceEndpoint;
    public TextField sendingServicePort;
    public TextField receivingServiceName;
    public TextField receivingServiceEndpoint;
    public TextField receivingServicePort;

    // Gateway Offloading
    public TextField serviceHost;
    public TextField serviceEndpoint;
    public TextField serviceName;

    // Cache Aside Pattern
    public TextField cachedEndpoints;
    public TextField backendService;

    // Gateway Aggregation Pattern
    public TextField ga_serviceName;
    public TextField ga_serviceEndpoint;
    public TextField ga_serviceHost;
    public TextField ga_servicePort;

    //Circuit Breaker Pattern
    public TextField cb_serviceName;
    public TextField cb_max_pending_requests;
    public TextField cb_max_connections;
    public TextField cb_failureThreshold;
    public TextField cb_retry_attempts;

    private File yamlFile;

    @FXML
    public void initialize() {
        // Register this controller with the mediator
        ControllerMediatorImpl.getInstance().registerPatternBuilderController(this);
        patternDropdown.setValue("None");
        logger.info("PatternBuilderController initialized.");
    }

    @FXML
    public void startKubernetes() {
        logger.info("User requested to start Kubernetes...");
        statusLabel.setText("Starting Kubernetes...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                javafx.application.Platform.runLater(() -> {
                    if (minikubeStarted) {
                        showAlert(Alert.AlertType.INFORMATION, "Minikube Start", "Minikube started successfully!");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Minikube Start", "Failed to start Minikube. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during Kubernetes start", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Minikube Start",
                        "An error occurred while starting Minikube: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void stopKubernetes() {
        logger.info("User requested to stop Kubernetes...");
        statusLabel.setText("Stopping Kubernetes...");

        new Thread(() -> {
            try {
                boolean minikubeStopped = KubernetesDeployer.stopMinikube();
                javafx.application.Platform.runLater(() -> {
                    if (minikubeStopped) {
                        showAlert(Alert.AlertType.INFORMATION, "Minikube Stop", "Minikube stopped successfully!");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Minikube Stop", "Failed to stop Minikube. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during Kubernetes stop", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Minikube Stop",
                        "An error occurred while stopping Minikube: " + e.getMessage()));
            }
        }).start();
    }

    @FXML
    public void handleFileUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml"));
        yamlFile = fileChooser.showOpenDialog(new Stage());
        if (yamlFile != null) {
            logger.info("YAML file selected: " + yamlFile.getAbsolutePath());
            statusLabel.setText("YAML file selected: " + yamlFile.getName());
        } else {
            statusLabel.setText("No YAML file selected.");
        }
    }

    @FXML
    public void deleteApplication() {
        logger.info("User requested to delete the application...");
        statusLabel.setText("Deleting application...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                if (minikubeStarted) {
                    KubernetesDeployer.deleteUserNamespace();
                    KubernetesDeployer.deletePatternNamespace();
                    logger.info("Application services deleted successfully...");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Application deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during application deletion", e);
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during application deletion."));
            }
        }).start();
    }
    @FXML
    public void deletePattern() {
        logger.info("User requested to delete the pattern...");
        statusLabel.setText("Deleting current pattern...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                if (minikubeStarted) {
                    KubernetesDeployer.deletePatternNamespace();
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Pattern deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during pattern deletion", e);
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during pattern deletion."));
            }
        }).start();
    }

    @FXML
    public void deployApplication() {
        if (yamlFile != null) {
            logger.info("User requested to deploy the application with configuration: " + yamlFile.getAbsolutePath());
            statusLabel.setText("Deploying applicaion configuration...");

            new Thread(() -> {
                try {
                    boolean minikubeStarted = KubernetesDeployer.startMinikube();
                    if (minikubeStarted) {
                        KubernetesDeployer.createNamespace("user");
                        KubernetesDeployer.applyYamlFile(yamlFile.getAbsolutePath());
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Configuration applied successfully."));
                    } else {
                        logger.warning("Failed to start Minikube. Deployment aborted.");
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deployment aborted."));
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during application deployment", e);
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during pattern deletion."));
                }
            }).start();
        } else {
            logger.warning("No YAML file selected. Deployment aborted.");
            showAlert(Alert.AlertType.WARNING, "Deploy Application", "Please select a YAML configuration file first.");
        }
    }

    @FXML
    public void handlePatternSelection() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User selected pattern: " + selectedPattern);

        patternFieldsBox.setVisible(!"None".equals(selectedPattern));

        asyncRequestReplyFields.setVisible(false);
        gatewayOffloadingFields.setVisible(false);
        gatewayAggregationFields.setVisible(false);
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
        statusLabel.setText("Deploying pattern: " + selectedPattern);

        Map<String, String> parameters = new HashMap<>();

        try {
            PatternGenerator generator = PatternGeneratorFactory.getGenerator(selectedPattern);
            String yamlFilePath = generator.getYamlFilePath();

            if ("Async Request Reply".equals(selectedPattern)) {
                parameters.put("SEND_SERVICE_NAME", sendingServiceName.getText());
                parameters.put("SEND_SERVICE_ENDPOINT", sendingServiceEndpoint.getText());
                parameters.put("SEND_SERVICE_PORT", sendingServicePort.getText());
                parameters.put("RECEIVE_SERVICE_NAME", receivingServiceName.getText());
                parameters.put("RECEIVE_SERVICE_ENDPOINT", receivingServiceEndpoint.getText());
                parameters.put("RECEIVE_SERVICE_PORT", receivingServicePort.getText());
            } else if ("Gateway Offloading".equals(selectedPattern)) {
                parameters.put("SERVICE_HOST", serviceHost.getText());
                parameters.put("SERVICE_ENDPOINT", serviceEndpoint.getText());
                parameters.put("SERVICE_NAME", serviceName.getText());
            } else if ("Gateway Aggregation".equals(selectedPattern)) {
                parameters.put("SERVICE_1_NAME", ga_serviceName.getText());
                parameters.put("SERVICE_1_ENDPOINT", ga_serviceEndpoint.getText());
                parameters.put("SERVICE_1_HOST", ga_serviceHost.getText());
                parameters.put("SERVICE_1_PORT", ga_servicePort.getText());
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

            KubernetesDeployer.createNamespace("pattern");
            generator.generatePattern(yamlFilePath, parameters);
            generator.deployPattern();

            logger.info("Pattern " + selectedPattern + " deployed successfully.");
            javafx.application.Platform.runLater(() -> statusLabel.setText("Pattern " + selectedPattern + " deployed successfully."));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during pattern generation or deployment", e);
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
