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
        String response = prometheusClient.queryPrometheus(promql);
        return extractMetricValue(response);
    }

    public Map<String, String> queryAllMetrics(String namespace) {
        Map<String, String> metrics = new HashMap<>();
        try {
            metrics.put("cpuUsage", queryAndExtract("avg(rate(container_cpu_usage_seconds_total{namespace=\"" + namespace + "\"}[5m]))"));
            metrics.put("nodeCpuUtilization", queryAndExtract("100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[1m])) by (instance))"));
            metrics.put("memoryUsage", queryAndExtract("sum(container_memory_usage_bytes{namespace=\"" + namespace + "\"})"));
            metrics.put("nodeMemoryUtilization", queryAndExtract("100 * (node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / node_memory_MemTotal_bytes"));
            metrics.put("responseTime", queryAndExtract("rate(http_request_duration_seconds_sum[1m]) / rate(http_request_duration_seconds_count[1m])"));
            metrics.put("totalSystemPower", queryAndExtract("sum(rate(node_power_watts_total[1m]))"));
            metrics.put("energyConsumption", queryAndExtract("sum(rate(container_energy_usage_joules[1m])) / sum(rate(http_requests_total[5m]))"));
        } catch (Exception e) {
            e.printStackTrace();
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        return metrics;
    }
}
