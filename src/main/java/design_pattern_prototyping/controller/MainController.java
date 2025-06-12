package design_pattern_prototyping.controller;

import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {


    @FXML private ComboBox<String> contextComboBox;
    @FXML private ComboBox<String> clusterModeComboBox;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button deleteButton;
    @FXML private Label statusLabel;

    private String selectedContext = null;
    private static final Logger logger = Logger.getLogger(MainController.class.getName());

    @FXML
    public void initialize() {
        clusterModeComboBox.getSelectionModel().selectFirst();
        updateClusterModeUI();
    }

    @FXML
    public void onClusterModeChanged() {
        updateClusterModeUI();
    }

    private void updateClusterModeUI() {
        String mode = clusterModeComboBox.getSelectionModel().getSelectedItem();
        boolean isMinikube = "Local".equals(mode);

        startButton.setVisible(isMinikube);
        stopButton.setVisible(isMinikube);
        deleteButton.setVisible(isMinikube);
        contextComboBox.setVisible(!isMinikube);

        if (isMinikube) {
            KubernetesUtil.setKubeContext("minikube");
        } else {
            KubernetesUtil.setKubeContext(null);
        }

        logger.info("Cluster mode changed to: " + mode);
    }

    @FXML
    public void onContextDropdownClicked() {
        new Thread(() -> {
            try {
                var contexts = KubernetesUtil.getAvailableContexts();
                javafx.application.Platform.runLater(() -> {
                    contextComboBox.getItems().setAll(contexts);
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to load contexts", e);
            }
        }).start();
    }

    @FXML
    public void onContextSelected() {
        selectedContext = contextComboBox.getSelectionModel().getSelectedItem();
        logger.info("User selected Kubernetes context: " + selectedContext);
        KubernetesUtil.setKubeContext(selectedContext);
    }

    @FXML
    public void startKubernetes() {
        logger.info("User requested to start Kubernetes...");
        statusLabel.setText("Starting Kubernetes...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesUtil.startMinikube();
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
                boolean minikubeStopped = KubernetesUtil.stopMinikube();
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
    public void deleteKubernetes() {
        logger.info("User requested to delete the cluster...");
        statusLabel.setText("Deleting Minikube cluster...");

        new Thread(() -> {
            try {
                boolean minikubeDeleted = KubernetesUtil.deleteMinikube();
                javafx.application.Platform.runLater(() -> {
                    if (minikubeDeleted) {
                        showAlert(Alert.AlertType.INFORMATION, "Minikube Delete", "Minikube deleted successfully!");
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Minikube Delete", "Failed to delete Minikube. Check logs for details.");
                    }
                });
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during cluster deletion", e);
                javafx.application.Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Minikube Delete",
                        "An error occurred while deleting Minikube: " + e.getMessage()));
            }
        }).start();
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
