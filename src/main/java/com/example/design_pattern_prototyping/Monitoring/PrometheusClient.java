package com.example.design_pattern_prototyping.Monitoring;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Streams;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO Replace HttpURLConnection library like Apache HttpClient
// TODO add retries, and timeouts.
// TODO Add Logging for Port Forwarding
public class PrometheusClient {
    private static final Logger logger = Logger.getLogger(PrometheusClient.class.getName());
    private static final int LOCAL_PORT = 9090;
    private static final int TARGET_PORT = 9090;

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

                logger.info("Port forwarding started for Prometheus pod: " + podName);

                // Set up the local server socket to handle connections
                try (Socket socket = new Socket("127.0.0.1", LOCAL_PORT)) {
                    logger.info("Connected to Prometheus port!");
                    Streams.copy(result.getInputStream(TARGET_PORT), socket.getOutputStream());
                    Streams.copy(socket.getInputStream(), result.getOutboundStream(TARGET_PORT));
                }

            } catch (IOException | ApiException e) {
                logger.log(Level.SEVERE, "Error starting Prometheus port forwarding", e);
            }
        }).start();
    }

    public String queryPrometheus(String query) throws Exception {
        String url = "http://127.0.0.1:9090/api/v1/query?query=" +
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            throw new Exception("Failed to query Prometheus: HTTP code " + responseCode);
        }
    }
}
