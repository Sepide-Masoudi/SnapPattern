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

    @FXML private VBox patternFieldsBox;
    @FXML private ComboBox<String> patternDropdown;
    @FXML private VBox asyncRequestReplyFields;
    @FXML private VBox gatewayOffloadingFields;

    @FXML private TextField sendingServiceName;
    @FXML private TextField sendingServiceEndpoint;
    @FXML private TextField sendingServicePort;
    @FXML private TextField receivingServiceName;
    @FXML private TextField receivingServiceEndpoint;
    @FXML private TextField receivingServicePort;

    @FXML private TextField serviceHost;
    @FXML private TextField serviceEndpoint;
    @FXML private TextField serviceName;

    private File yamlFile;

    @FXML
    public void startKubernetes() {
        logger.info("User requested to start Kubernetes...");

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
        }
    }

    @FXML
    public void deleteApplication() {
        logger.info("User requested to delete the application...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                if (minikubeStarted) {
                    KubernetesDeployer.deleteUserNamespace();
                    KubernetesDeployer.deletePatternNamespace();
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during application deletion", e);
            }
        }).start();
    }

    @FXML
    public void deletePattern() {
        logger.info("User requested to delete the pattern...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                if (minikubeStarted) {
                    KubernetesDeployer.deletePatternNamespace();
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during pattern deletion", e);
            }
        }).start();
    }

    @FXML
    public void deployApplication() {
        if (yamlFile != null) {
            logger.info("User requested to deploy the application with configuration: " + yamlFile.getAbsolutePath());

            new Thread(() -> {
                try {
                    boolean minikubeStarted = KubernetesDeployer.startMinikube();
                    if (minikubeStarted) {
                        KubernetesDeployer.createNamespace();
                        KubernetesDeployer.applyYamlFile(yamlFile.getAbsolutePath());
                    } else {
                        logger.warning("Failed to start Minikube. Deployment aborted.");
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during application deployment", e);
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

        patternFieldsBox.setVisible(true);
        if ("Async Request Reply".equals(selectedPattern)) {
            asyncRequestReplyFields.setVisible(true);
            gatewayOffloadingFields.setVisible(false);
        } else if ("Gateway Offloading".equals(selectedPattern)) {
            asyncRequestReplyFields.setVisible(false);
            gatewayOffloadingFields.setVisible(true);
        } else {
            asyncRequestReplyFields.setVisible(false);
            gatewayOffloadingFields.setVisible(false);
        }
    }

    @FXML
    public void buildPattern() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User requested to build pattern: " + selectedPattern);
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
            }

            generator.generatePattern(yamlFilePath, parameters);
            generator.deployPattern();

            logger.info("Pattern " + selectedPattern + " built and deployed successfully.");
            showAlert(Alert.AlertType.INFORMATION, "Build Pattern", "Pattern " + selectedPattern + " built and deployed successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during pattern generation or deployment", e);
            showAlert(Alert.AlertType.ERROR, "Build Pattern", "An error occurred while building the pattern: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
