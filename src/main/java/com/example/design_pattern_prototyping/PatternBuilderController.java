package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.pattern_generator.AsyncRequestReplyGenerator;
import com.example.design_pattern_prototyping.pattern_generator.GatewayOffloadingGenerator;
import com.example.design_pattern_prototyping.pattern_generator.KubernetesDeployer;
import com.example.design_pattern_prototyping.pattern_generator.PatternGeneratorFactory;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

import java.io.File;

public class PatternBuilderController {

    @FXML private HBox step2Box;
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
    public void handleFileUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml"));
        yamlFile = fileChooser.showOpenDialog(new Stage());
        if (yamlFile != null) {
            System.out.println("YAML file selected: " + yamlFile.getAbsolutePath());
        }
    }

    @FXML
    public void deleteNamespace() {
        System.out.println("Deleting user application namespace...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesDeployer.startMinikube();

                if (minikubeStarted) {
                    KubernetesDeployer.deleteNamespace();
                } else {
                    System.out.println("Failed to start Minikube. Deletion aborted.");
                }
            } catch (Exception e) {
                System.out.println("Error during namespace deletion: " + e.getMessage());
            }
        }).start();
    }

    @FXML
    public void deployCluster() {
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

                        javafx.application.Platform.runLater(() -> step2Box.setVisible(true));
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
        if ("Async Request Reply".equals(selectedPattern)) {
            asyncRequestReplyFields.setVisible(true);
            gatewayOffloadingFields.setVisible(false);
        } else if ("Gateway Offloading".equals(selectedPattern)) {
            asyncRequestReplyFields.setVisible(false);
            gatewayOffloadingFields.setVisible(true);
            patternFieldsBox.setVisible(true);
        } else {
            asyncRequestReplyFields.setVisible(false);
            gatewayOffloadingFields.setVisible(false);
        }
    }

    @FXML
    public void buildPattern() {
        String selectedPattern = patternDropdown.getValue();
        Map<String, String> parameters = new HashMap<>();
        String yamlFilePath = "src/main/resources/patterns/" + selectedPattern.replaceAll(" ", "") + "/nginx-ingress.yml";

        try {
            if ("Async Request Reply".equals(selectedPattern)) {
                AsyncRequestReplyGenerator generator = PatternGeneratorFactory.getGenerator(selectedPattern);

                parameters.put("SEND_SERVICE_NAME", sendingServiceName.getText());
                parameters.put("SEND_SERVICE_ENDPOINT", sendingServiceEndpoint.getText());
                parameters.put("SEND_SERVICE_PORT", sendingServicePort.getText());
                parameters.put("RECEIVE_SERVICE_NAME", receivingServiceName.getText());
                parameters.put("RECEIVE_SERVICE_ENDPOINT", receivingServiceEndpoint.getText());
                parameters.put("RECEIVE_SERVICE_PORT", receivingServicePort.getText());

                generator.generatePattern(yamlFilePath, parameters);
                generator.deployPattern();

            } else if ("Gateway Offloading".equals(selectedPattern)) {
                GatewayOffloadingGenerator generator = new GatewayOffloadingGenerator();

                parameters.put("SERVICE_HOST", serviceHost.getText());
                parameters.put("SERVICE_ENDPOINT", serviceEndpoint.getText());
                parameters.put("SERVICE_NAME", serviceName.getText());

                generator.generatePattern(yamlFilePath, parameters);
                generator.deployPattern();

            } else {
                System.out.println("Unsupported pattern: " + selectedPattern);
            }

            System.out.println("Pattern " + selectedPattern + " built and deployed successfully.");

        } catch (Exception e) {
            System.err.println("Error during pattern generation or deployment: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
