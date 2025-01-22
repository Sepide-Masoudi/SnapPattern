package com.example.design_pattern_prototyping.Monitoring;

import com.example.design_pattern_prototyping.Kubernetes.KubernetesClientAPI;
import com.example.design_pattern_prototyping.MetricsController;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JaegerClient {

    private static final Logger logger = Logger.getLogger(MetricsController.class.getName());
    private static final String JAEGER_ENDPOINT = "http://localhost:16686";
    private static final String NAMESPACE = "monitoring";
    private static final String SERVICE_NAME = "jaeger";
    private static final int LOCAL_PORT = 16686; // Port on localhost
    private static final int TARGET_PORT = 16686; // Port on the Jaeger pod

    public static void startJaegerPortForwarding() {
        try {
            // Initialize the Kubernetes API client
            ApiClient client = Config.defaultClient();
            KubernetesClientAPI kubernetesClientAPI = new KubernetesClientAPI(client);

            // Call the generalized port-forwarding method
            kubernetesClientAPI.startPortForwarding(NAMESPACE, SERVICE_NAME, LOCAL_PORT, TARGET_PORT);

        } catch (Exception e) {
            logger.severe("Failed to initialize Jaeger port forwarding: " + e.getMessage());
        }
    }

    private String fetchTracesByService(String serviceName) throws Exception {
        String queryUrl = JAEGER_ENDPOINT + "/api/traces?service=" + serviceName;
        return executeGetRequest(queryUrl);
    }

    private String executeGetRequest(String queryUrl) throws Exception {
        URL url = new URL(queryUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Failed : HTTP error code : " + responseCode);
        }

        BufferedReader br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
        StringBuilder response = new StringBuilder();
        String output;

        while ((output = br.readLine()) != null) {
            response.append(output);
        }

        connection.disconnect();
        return response.toString();
    }
    public void collectTraceData(String namespace, String outputFile) {
        try (FileWriter writer = new FileWriter(outputFile)) {
            // Initialize the Kubernetes API client
            ApiClient client = Config.defaultClient();
            KubernetesClientAPI kubernetesClientAPI = new KubernetesClientAPI(client);

            List<String> services = kubernetesClientAPI.getServicesInNamespace(namespace);

            writer.write("{\"services\": [\n");

            for (int i = 0; i < services.size(); i++) {
                String service = services.get(i);
                logger.info("Fetching traces for service: " + service);

                String traceData = fetchTracesByService(service);
                writer.write("  {\"service\": \"" + service + "\", \"traces\": " + traceData + "}");

                if (i < services.size() - 1) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }

            writer.write("]}");
            logger.info("Trace data has been successfully saved to " + outputFile);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error collecting trace data.", e);
        }
    }
}