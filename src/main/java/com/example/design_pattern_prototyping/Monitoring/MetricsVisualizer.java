package com.example.design_pattern_prototyping.Monitoring;

import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.json.JSONObject;

public class MetricsVisualizer {

    // TODO Recieve Metric Plots back from the Python service to display in Window
    public void displayMetrics(Map<String, String> metrics) {
        System.out.println("Metrics:");
        for (Map.Entry<String, String> entry : metrics.entrySet()) {
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }
    // TODO Replace HttpURLConnection library like Apache HttpClient
    // and add retries, and timeouts.
    public static void sendMetricsToPython(Map<String, String> metrics) {
        try {
            // Create JSON object using org.json library
            JSONObject jsonMetrics = new JSONObject();
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                jsonMetrics.put(entry.getKey(), entry.getValue());
            }

            // Send HTTP POST request
            URL url = new URL("http://127.0.0.1:5000/metrics");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setDoOutput(true);

            // Write JSON data to request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonMetrics.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Handle response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Metrics sent successfully.");
            } else {
                System.err.println("Failed to send metrics. HTTP Code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // TODO test how these results are formated, maybe use the JSON or requires conversion
    public void saveMetricsToCSV(Map<String, String> metrics) {
        String fileName = "metrics.csv";
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("Metric,Value\n");

            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue() + "\n");
            }
            System.out.println("Metrics saved to " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

