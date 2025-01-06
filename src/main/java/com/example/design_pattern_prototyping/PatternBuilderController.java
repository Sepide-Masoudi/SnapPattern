package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesDeployer;
import com.example.design_pattern_prototyping.pattern_generator.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

import java.io.File;

public class PatternBuilderController {

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
        System.out.println("Starting Kubernetes...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();
                if (minikubeStarted) {
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Minikube Start");
                        alert.setHeaderText(null);
                        alert.setContentText("Minikube started successfully!");
                        alert.showAndWait();
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Minikube Start");
                        alert.setHeaderText(null);
                        alert.setContentText("Failed to start Minikube. Check console for details.");
                        alert.showAndWait();
                    });
                }
            } catch (Exception e) {
                System.err.println("Error starting Minikube: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Minikube Start");
                    alert.setHeaderText(null);
                    alert.setContentText("An error occurred while starting Minikube: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    @FXML
    public void stopKubernetes() {
        System.out.println("Stopping Kubernetes...");

        new Thread(() -> {
            try {
                boolean minikubeStopped = KubernetesDeployer.stopMinikube();
                if (minikubeStopped) {
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Minikube Stop");
                        alert.setHeaderText(null);
                        alert.setContentText("Minikube stopped successfully!");
                        alert.showAndWait();
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Minikube Stop");
                        alert.setHeaderText(null);
                        alert.setContentText("Failed to stop Minikube. Check console for details.");
                        alert.showAndWait();
                    });
                }
            } catch (Exception e) {
                System.err.println("Error stopping Minikube: " + e.getMessage());
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Minikube Stop");
                    alert.setHeaderText(null);
                    alert.setContentText("An error occurred while stopping Minikube: " + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }


    @FXML
    public void handleFileUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml"));
        yamlFile = fileChooser.showOpenDialog(new Stage());
        if (yamlFile != null) {
            System.out.println("YAML file selected: " + yamlFile.getAbsolutePath());
        }
    }

    //Delete the user namespace and all services within
    @FXML
    public void deleteApplication() {
        System.out.println("Deleting user application...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();

                if (minikubeStarted) {
                    KubernetesDeployer.deleteUserNamespace();
                    KubernetesDeployer.deletePatternNamespace();
                } else {
                    System.out.println("Failed to start Minikube. Deletion aborted.");
                }
            } catch (Exception e) {
                System.out.println("Error during namespace deletion: " + e.getMessage());
            }
        }).start();
    }
    //Delete the Pattern namespaces and all services within
    @FXML
    public void deletePattern() {
        System.out.println("Deleting Pattern...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();

                if (minikubeStarted) {
                    KubernetesDeployer.deletePatternNamespace();
                } else {
                    System.out.println("Failed to start Minikube. Deletion aborted.");
                }
            } catch (Exception e) {
                System.out.println("Error during namespace deletion: " + e.getMessage());
            }
        }).start();
    }

    //Deploy YAML Configuration for user application
    @FXML
    public void deployApplication() {
        if (yamlFile != null) {
            System.out.println("Deploying cluster with configuration: " + yamlFile.getAbsolutePath());

            new Thread(() -> {
                try {
                    boolean minikubeStarted = KubernetesDeployer.startMinikube();

                    if (minikubeStarted) {
                        System.out.println("Minikube started successfully. Deploying YAML file...");
                        KubernetesDeployer.createNamespace();
                        System.out.println("Namespace created successfully.");
                        KubernetesDeployer.applyYamlFile(yamlFile.getAbsolutePath());
                        System.out.println("YAML file deployed successfully.");
                    } else {
                        System.out.println("Failed to start Minikube. Deployment aborted.");
                    }
                } catch (Exception e) {
                    System.out.println("Error during deployment: " + e.getMessage());
                }
            }).start();
        } else {
            System.out.println("Please select a YAML configuration file first.");
        }
    }

    @FXML
    public void handlePatternSelection() {
        String selectedPattern = patternDropdown.getValue();
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
        //Pattern selection
        String selectedPattern = patternDropdown.getValue();
        Map<String, String> parameters = new HashMap<>();

        try {
            // Get Pattern Generator Subclass
            PatternGenerator generator = PatternGeneratorFactory.getGenerator(selectedPattern);

            // Get the YAML file path specific to the generator
            String yamlFilePath = generator.getYamlFilePath();

            //Pattern specific Parameters
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

            // Generate and deploy the pattern
            generator.generatePattern(yamlFilePath, parameters);
            generator.deployPattern();

            System.out.println("Pattern " + selectedPattern + " built and deployed successfully.");
        } catch (Exception e) {
            System.err.println("Error during pattern generation or deployment: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
