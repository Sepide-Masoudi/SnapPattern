package com.example.design_pattern_prototyping.Monitoring;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesClientAPI;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;


public class GrafanaClient {
    private static final Logger logger = Logger.getLogger(GrafanaClient.class.getName());
    private static final String GRAFANA_DASHBOARD_URL = "http://127.0.0.1:3000/d/NhnADUW4zIBM/kepler-exporter-dashboard?orgId=1";
    private static final String NAMESPACE = "monitoring";
    private static final String SERVICE_NAME = "prometheus-grafana";
    private static final int LOCAL_PORT = 3000;
    private static final int TARGET_PORT = 3000;

    public static void startPortForwarding() {
        try {
            // Initialize the Kubernetes API client
            ApiClient client = Config.defaultClient();
            KubernetesClientAPI kubernetesClientAPI = new KubernetesClientAPI(client);

            // Call the generalized port-forwarding method
            kubernetesClientAPI.startPortForwarding(NAMESPACE, SERVICE_NAME, LOCAL_PORT, TARGET_PORT);

        } catch (Exception e) {
            logger.severe("Failed to initialize port forwarding: " + e.getMessage());
        }
    }

    public static boolean isPortForwardingActive() {
        try (Socket socket = new Socket("127.0.0.1", TARGET_PORT)) {
            logger.info("Port forwarding is active.");
            socket.close();
            return true;
        } catch (IOException e) {
            logger.info("Port forwarding is not active.");
            return false;
        }
    }

    public void start(Stage primaryStage) {
        logger.info("Starting Grafana WebView...");

        // Create a WebView instance
        WebView webView = new WebView();
        webView.getEngine().load(GRAFANA_DASHBOARD_URL);

        // Set up the layout
        BorderPane root = new BorderPane();
        root.setCenter(webView);

        // Create and set the scene
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Grafana Metrics Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();

        logger.info("Grafana WebView started successfully.");
    }
}
