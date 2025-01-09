package com.example.design_pattern_prototyping.Monitoring;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Streams;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrafanaClient {
    private static final Logger logger = Logger.getLogger(GrafanaClient.class.getName());
    private static final String GRAFANA_DASHBOARD_URL = "http://127.0.0.1:3000/d/abc123/my-dashboard?orgId=1";
    private static final int LOCAL_PORT = 3000;
    private static final int TARGET_PORT = 3000;

    public static void startPortForwarding() {
        new Thread(() -> {
            try {
                // Set up the Kubernetes API client
                ApiClient client = Config.defaultClient();
                Configuration.setDefaultApiClient(client);

                // Set the PortForward object and target port
                PortForward forward = new PortForward();
                List<Integer> ports = new ArrayList<>();

                ports.add(TARGET_PORT);

                // Fetch the pod name from environment variables (POD_NAME)
                String podName = System.getenv("POD_NAME");

                if (podName == null || podName.isEmpty()) {
                    logger.severe("POD_NAME environment variable is not set.");
                    return;
                }

                // Forward the port on the specified pod in the "monitoring" namespace
                PortForward.PortForwardResult result = forward.forward("monitoring", podName, ports);

                logger.info("Port forwarding started for pod: " + podName);

                // Set up the local server socket to handle connections
                ServerSocket serverSocket = new ServerSocket(LOCAL_PORT);
                Socket socket = serverSocket.accept();
                logger.info("Connected to Grafana port!");

                // Set up threads to copy data between the port-forwarded stream and the socket
                new Thread(() -> {
                    try {
                        Streams.copy(result.getInputStream(TARGET_PORT), socket.getOutputStream());
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Error in input stream copy", e);
                    }
                }).start();

                new Thread(() -> {
                    try {
                        Streams.copy(socket.getInputStream(), result.getOutboundStream(TARGET_PORT));
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "Error in output stream copy", e);
                    }
                }).start();

                // Keep the port forwarding open for a while before stopping
                Thread.sleep(10 * 1000);

            } catch (IOException | ApiException | InterruptedException e) {
                logger.log(Level.SEVERE, "Error starting Grafana port forwarding", e);
            }
        }).start();
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
