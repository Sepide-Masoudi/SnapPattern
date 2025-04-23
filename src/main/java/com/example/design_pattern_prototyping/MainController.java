package com.example.design_pattern_prototyping;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesDeployer;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

import java.util.logging.Level;
import java.util.logging.Logger;

public class MainController {

    private static final Logger logger = Logger.getLogger(MainController.class.getName());

    public Label statusLabel;

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
    public void deleteKubernetes() {
        logger.info("User requested to delete the cluster...");
        statusLabel.setText("Deleting Minikube cluster...");

        new Thread(() -> {
            try {
                boolean minikubeDeleted = KubernetesDeployer.deleteMinikube();
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
