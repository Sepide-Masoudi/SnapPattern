package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesClientAPI;
import com.example.design_pattern_prototyping.Kubernetes.KubernetesDeployer;
import io.kubernetes.client.util.Config;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ResourceController {

    @FXML public TextField namespaceField;
    @FXML public ListView<String> serviceListView;
    @FXML public ListView<String> podListView;
    @FXML public GridPane podDetailsGrid;
    @FXML public CheckBox filterNamespaceCheckbox;

    private KubernetesClientAPI kubernetesClient;

    private static final Logger logger = Logger.getLogger(ResourceController.class.getName());

    @FXML
    private void loadResources() {
        String namespace = namespaceField.getText().trim();
        serviceListView.getItems().clear();
        podListView.getItems().clear();
        podDetailsGrid.getChildren().clear();

        try {
            // Check Minikube status before trying to connect
            if (!KubernetesDeployer.statusMinikube()) {
                logger.warning("Minikube cluster is not running. Cannot load Kubernetes resources.");
                showAlert("Cluster not available", "Minikube is not running. Please start the cluster first.");
                return;
            }

            if (kubernetesClient == null) {
                logger.info("Initializing Kubernetes client...");
                var apiClient = Config.defaultClient();
                kubernetesClient = new KubernetesClientAPI(apiClient);
                logger.info("Kubernetes client initialized successfully.");
            }

            if (filterNamespaceCheckbox.isSelected()) {
                logger.info("Loading resources filtered by namespace: " + namespace);
                serviceListView.getItems().addAll(kubernetesClient.getServicesInNamespace(namespace));
                podListView.getItems().addAll(kubernetesClient.getPodsInNamespace(namespace));
            } else {
                logger.info("Loading resources from all namespaces");

                Map<String, String> allServices = kubernetesClient.getAllServicesWithNamespaces();
                allServices.forEach((svc, ns) ->
                        serviceListView.getItems().add(svc + " (ns: " + ns + ")")
                );

                Map<String, String> allPods = kubernetesClient.getAllPodsWithNamespaces();
                allPods.forEach((pod, ns) ->
                        podListView.getItems().add(pod + " (ns: " + ns + ")")
                );
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error loading resources", e);
            showAlert("Resource Loading Error", e.getMessage());
        }
    }

    @FXML
    private void showPodDetails() {
        podDetailsGrid.getChildren().clear();
        String selectedPod = podListView.getSelectionModel().getSelectedItem();

        if (selectedPod != null) {
            String namespace;
            String podName;

            if (filterNamespaceCheckbox.isSelected()) {
                namespace = namespaceField.getText().trim();
                podName = selectedPod;
            } else {
                int idx = selectedPod.lastIndexOf(" (ns: ");
                if (idx != -1) {
                    podName = selectedPod.substring(0, idx);
                    namespace = selectedPod.substring(idx + 6, selectedPod.length() - 1);
                } else {
                    showAlert("Selection Error", "Could not parse namespace for pod: " + selectedPod);
                    logger.warning("Parsing error for pod details: " + selectedPod);
                    return;
                }
            }

            try {
                Map<String, String> details = kubernetesClient.getPodDetails(namespace, podName);
                int row = 0;
                for (Map.Entry<String, String> entry : details.entrySet()) {
                    podDetailsGrid.add(new Label(entry.getKey() + ":"), 0, row);
                    podDetailsGrid.add(new Text(entry.getValue()), 1, row++);
                }
                logger.info("Pod details loaded successfully for pod: " + podName + ", namespace: " + namespace);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error fetching pod details", e);
                showAlert("Pod Details Error", e.getMessage());
            }
        } else {
            logger.warning("No pod selected to fetch details.");
        }
    }

    @FXML
    private void handleServiceSelection() {
        String selectedService = serviceListView.getSelectionModel().getSelectedItem();
        if (selectedService == null) return;

        try {
            String namespace;
            String serviceName;

            if (filterNamespaceCheckbox.isSelected()) {
                namespace = namespaceField.getText().trim();
                serviceName = selectedService;
            } else {
                int idx = selectedService.lastIndexOf(" (ns: ");
                if (idx != -1) {
                    serviceName = selectedService.substring(0, idx);
                    namespace = selectedService.substring(idx + 6, selectedService.length() - 1);
                } else {
                    logger.warning("Could not parse service selection: " + selectedService);
                    return;
                }
            }

            logger.info("Service selected: " + serviceName + " in namespace: " + namespace);

            List<String> matchingPods = kubernetesClient.getPodsForService(namespace, serviceName);
            if (matchingPods.isEmpty()) {
                logger.info("No pods found for service: " + serviceName);
                return;
            }

            podListView.getSelectionModel().clearSelection();
            podDetailsGrid.getChildren().clear();
            podListView.getItems().stream()
                    .filter(item -> {
                        for (String pod : matchingPods) {
                            if (item.startsWith(pod)) return true;
                        }
                        return false;
                    })
                    .findFirst()
                    .ifPresent(pod -> {
                        podListView.getSelectionModel().select(pod);
                        showPodDetails();
                    });

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to handle service selection", e);
            showAlert("Service Selection Error", e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.show();
    }
}