package com.example.design_pattern_prototyping.Monitoring;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO Replace HttpURLConnection library like Apache HttpClient
// TODO add retries, and timeouts.
// TODO Add Logging for Port Forwarding
public class PrometheusClient {
    private static final Logger logger = Logger.getLogger(PrometheusClient.class.getName());
    private static Process prometheusProcess;

    public static void startPortForwarding() {
        stopPortForwarding();
        new Thread(() -> {
            try {
                prometheusProcess = new ProcessBuilder(
                        "kubectl", "port-forward", "service/prometheus", "9090:9090", "-n", "monitoring"
                ).start();
                logger.info("Prometheus port forwarding started on http://localhost:9090");
                prometheusProcess.waitFor();
            } catch (InterruptedException | IOException e) {
                logger.log(Level.SEVERE, "Error starting Prometheus port forwarding", e);
            }
        }).start();
    }

    public static void stopPortForwarding() {
        if (prometheusProcess != null && prometheusProcess.isAlive()) {
            prometheusProcess.destroy();
            logger.info("Prometheus port forwarding stopped.");
        }
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
