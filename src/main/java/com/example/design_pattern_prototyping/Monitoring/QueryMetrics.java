package com.example.design_pattern_prototyping.Monitoring;

import java.util.HashMap;
import java.util.Map;

public class QueryMetrics {

    private final PrometheusClient prometheusClient;

    public QueryMetrics() {
        this.prometheusClient = new PrometheusClient();
    }
    /**Performance metrics**/
    public String queryCpuUsage(String namespace) throws Exception {
        String promql = "avg(rate(container_cpu_usage_seconds_total{namespace=\"" + namespace + "\"}[5m]))";
        return prometheusClient.queryPrometheus(promql);
    }

    public String queryNodeCpuUsage() throws Exception {
        String promql = "100 * (1 - avg(rate(node_cpu_seconds_total{mode=\"idle\"}[1m])) by (instance))";
        return prometheusClient.queryPrometheus(promql);
    }

    public String queryMemoryUsage(String namespace) throws Exception {
        String promql = "sum(container_memory_usage_bytes{namespace=\"" + namespace + "\"})";
        return prometheusClient.queryPrometheus(promql);
    }

    public String queryNodeMemoryUsage() throws Exception {
        String promql = "100 * (node_memory_MemTotal_bytes - node_memory_MemAvailable_bytes) / node_memory_MemTotal_bytes";
        return prometheusClient.queryPrometheus(promql);
    }

    public String queryResponseTime() throws Exception {
        String promql = "rate(http_request_duration_seconds_sum[1m]) / rate(http_request_duration_seconds_count[1m])";
        return prometheusClient.queryPrometheus(promql);
    }

    /**Energy metrics**/
    public String queryTotalSystemPower() throws Exception {
        String promql = "sum(rate(node_power_watts_total[1m]))";
        return prometheusClient.queryPrometheus(promql);
    }

    public String queryEnergyConsumption() throws Exception {
        String promql = "sum(rate(container_energy_usage_joules[1m])) / sum(rate(http_requests_total[5m]))";
        return prometheusClient.queryPrometheus(promql);
    }

    public Map<String, String> queryAllMetrics(String namespace) {
        Map<String, String> metrics = new HashMap<>();
        try {
            metrics.put("cpuUsage", queryCpuUsage(namespace));
            metrics.put("nodeCpuUtilization", queryNodeCpuUsage());
            metrics.put("memoryUsage", queryMemoryUsage(namespace));
            metrics.put("nodeMemoryUtilization", queryNodeMemoryUsage());
            metrics.put("responseTime", queryResponseTime());
            metrics.put("totalSystemPower", queryTotalSystemPower());
            metrics.put("energyConsumption", queryEnergyConsumption());
        } catch (Exception e) {
            e.printStackTrace();
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        return metrics;
    }
}
