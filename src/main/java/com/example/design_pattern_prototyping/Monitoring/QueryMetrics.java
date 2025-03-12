package com.example.design_pattern_prototyping.Monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryMetrics {

    private static final Logger logger = Logger.getLogger(QueryMetrics.class.getName());

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
            if (results.isArray() && !results.isEmpty()) {
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
        logger.info("Executing PromQL: " + promql);
        String response = prometheusClient.queryPrometheus(promql);
        logger.info("Response: " + response);
        return response;
    }

    public Map<String, String> queryAllMetrics(String namespace) {
        Map<String, String> metrics = new HashMap<>();
        try {
            metrics.put("containerJoulesTotal", queryAndExtract(
                    "sum(kepler_container_joules_total{container_namespace=~\"user|pattern\"}) by (container_name)"));
            metrics.put("containerCpuCyclesTotal", queryAndExtract(
                    "sum(kepler_container_cpu_cycles_total{container_namespace=~\"user|pattern\"}) by (container_name)\n"));
            metrics.put("containerCacheMissTotal", queryAndExtract(
                    "sum(kepler_container_cache_miss_total{container_namespace=~\"user|pattern\"}) by (container_name)\n"));
            metrics.put("containerCpuInstructions", queryAndExtract(
                    "sum(kepler_container_cpu_instructions_total{container_namespace=~\"user|pattern\"}) by (container_name)"));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to query metrics", e);
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        logger.info("Metrics: " + metrics);
        return metrics;
    }
}