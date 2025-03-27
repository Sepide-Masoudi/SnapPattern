package com.example.design_pattern_prototyping.Monitoring;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryMetrics {

    private static final Logger logger = Logger.getLogger(QueryMetrics.class.getName());

    private final PrometheusClient prometheusClient;

    public QueryMetrics() {
        this.prometheusClient = new PrometheusClient();
    }

    private String queryAndExtract(String promql) throws Exception {
        logger.info("Executing PromQL: " + promql);
        String response = prometheusClient.queryPrometheus(promql);
        logger.info("Response: " + response);
        return response;
    }

    public Map<String, String> queryAllMetrics() {
        Map<String, String> metrics = new HashMap<>();
        try {
            // Kepler metrics
            metrics.put("containerJoulesTotal", queryAndExtract(
                    "sum(kepler_container_joules_total{container_namespace=~\"user|pattern\"}) by (container_name)"));
            metrics.put("containerCpuCyclesTotal", queryAndExtract(
                    "sum(kepler_container_cpu_cycles_total{container_namespace=~\"user|pattern\"}) by (container_name)\n"));
            metrics.put("containerCacheMissTotal", queryAndExtract(
                    "sum(kepler_container_cache_miss_total{container_namespace=~\"user|pattern\"}) by (container_name)\n"));
            metrics.put("containerCpuInstructions", queryAndExtract(
                    "sum(kepler_container_cpu_instructions_total{container_namespace=~\"user|pattern\"}) by (container_name)"));
            // Span metrics
            metrics.put("avg_HTTP_client_request_duration", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_sum{
                              exported_instance=~"user\\\\..*"
                            }[5m])) by (exported_job)
                            /
                            sum(rate(http_client_request_duration_seconds_count{
                              exported_instance=~"user\\\\..*"
                            }[5m])) by (exported_job)
                            """));
            metrics.put("requestRate_RPS", queryAndExtract(
                    "sum(rate(http_client_request_duration_seconds_count{exported_instance=~\"user\\\\..*\"}[5m])) by (exported_job)"));
            metrics.put("averageLatency", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_sum{exported_instance=~"user\\\\..*"}[5m])) by (exported_job)
                            /\s
                            sum(rate(http_client_request_duration_seconds_count{exported_instance=~"user\\\\..*"}[5m])) by (exported_job)"""));
            metrics.put("95PercentileLatency", queryAndExtract(
                    "histogram_quantile(0.95, sum(rate(http_client_request_duration_seconds_bucket{exported_instance=~\"user\\\\..*\"}[5m])) by (le, exported_job))"));
            metrics.put("ErrorRate", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_count{
                              exported_instance=~"user\\\\..*",
                              http_response_status_code!~"2.."
                            }[5m])) by (exported_job)
                            /
                            sum(rate(http_client_request_duration_seconds_count{
                              exported_instance=~"user\\\\..*"
                            }[5m])) by (exported_job)"""));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to query metrics", e);
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        logger.info("Metrics: " + metrics);
        return metrics;
    }
}