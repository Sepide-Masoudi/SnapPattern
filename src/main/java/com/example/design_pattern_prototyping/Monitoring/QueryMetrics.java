package com.example.design_pattern_prototyping.Monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

public class QueryMetrics {

    private final PrometheusClient prometheusClient;
    private final ObjectMapper objectMapper;

    public QueryMetrics() {
        this.prometheusClient = new PrometheusClient();
        this.objectMapper = new ObjectMapper();
    }

    private String extractMetricValue(String jsonResponse) throws Exception {
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        if ("success".equals(rootNode.path("status").asText())) {
            JsonNode results = rootNode.path("data").path("result");
            if (results.isArray() && results.size() > 0) {
                // Extract the first result's value array (timestamp and metric value)
                JsonNode valueArray = results.get(0).path("value");
                if (valueArray.isArray() && valueArray.size() == 2) {
                    return valueArray.get(1).asText(); // Extract metric value
                }
            }
        }
        return "0"; // Default value if no metric is found
    }

    private String queryAndExtract(String promql) throws Exception {
        System.out.println("Executing PromQL: " + promql); // Log PromQL query
        String response = prometheusClient.queryPrometheus(promql);
        System.out.println("Response: " + response); // Log raw response
        return extractMetricValue(response);
    }

    public Map<String, String> queryAllMetrics(String namespace) {
        Map<String, String> metrics = new HashMap<>();
        try {
            // Kepler-specific metrics
            metrics.put("cpuUsage", queryAndExtract("rate(kepler_container_cpu_usage_total[5m])"));
            metrics.put("memoryUsage", queryAndExtract("kepler_container_memory_usage_bytes"));
            metrics.put("nodeEnergyConsumption", queryAndExtract("rate(kepler_node_energy_joules_total[5m])"));
            metrics.put("containerEnergyConsumption", queryAndExtract("rate(kepler_container_energy_joules_total[5m])"));
            metrics.put("energyEfficiency", queryAndExtract(
                    "rate(kepler_container_energy_joules_total[5m]) / rate(kepler_container_cpu_usage_total[5m])"));
        } catch (Exception e) {
            e.printStackTrace();
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        return metrics;
    }
}
