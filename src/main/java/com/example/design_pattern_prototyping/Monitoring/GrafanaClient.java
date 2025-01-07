package com.example.design_pattern_prototyping.Monitoring;

import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO add retries, and timeouts.
// TODO Add Logging for Port Forwarding
public class GrafanaClient {
    private static final Logger logger = Logger.getLogger(GrafanaClient.class.getName());
    private static Process grafanaProcess;

    public static void startPortForwarding() {
        stopPortForwarding();
        new Thread(() -> {
            try {
                grafanaProcess = new ProcessBuilder(
                        "kubectl", "port-forward", "service/grafana", "3000:3000", "-n", "monitoring"
                ).start();
                logger.info("Grafana port forwarding started on http://localhost:3000");
                grafanaProcess.waitFor();
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "Error starting Grafana port forwarding", e);
            }
        }).start();
    }

    public static void stopPortForwarding() {
        if (grafanaProcess != null && grafanaProcess.isAlive()) {
            grafanaProcess.destroy();
            logger.info("Grafana port forwarding stopped.");
        }
    }

    public static void startGrafanaDashboard() {
        Stage primaryStage = new Stage();
        primaryStage.setTitle("Grafana Metrics Dashboard");

        // Set up the scene and stage
        BorderPane root = new BorderPane();

        Scene scene = new Scene(root, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
