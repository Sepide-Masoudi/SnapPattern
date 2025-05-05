package com.example.design_pattern_prototyping.Monitoring;

import com.example.design_pattern_prototyping.util.UILogger;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QueryMetrics {

    private static final Logger logger = Logger.getLogger(QueryMetrics.class.getName());
    private UILogger uiLogger;

    public void setLogger(UILogger logger) {
        this.uiLogger = logger;
    }

    private final PrometheusClient prometheusClient;

    public QueryMetrics() {
        this.prometheusClient = new PrometheusClient();
    }

    private String queryAndExtract(String promql) throws Exception {
        logger.info("Executing PromQL: " + promql);
        uiLogger.info("Executing PromQL: " + promql);
        String response = prometheusClient.queryPrometheus(promql);
        logger.info("Response: " + response);
        uiLogger.info("Response: " + response);
        return response;
    }

    public Map<String, String> queryAllMetrics() {
        Map<String, String> metrics = new HashMap<>();
        try {
            // Kepler metrics
            metrics.put("containerJoulesTotal", queryAndExtract(
                    "sum(rate(kepler_container_joules_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("containerCpuCyclesTotal", queryAndExtract(
                    "sum(rate(kepler_container_cpu_cycles_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("containerCacheMissTotal", queryAndExtract(
                    "sum(rate(kepler_container_cache_miss_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("containerCpuInstructions", queryAndExtract(
                    "sum(rate(kepler_container_cpu_instructions_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)"));
            // Span metrics

            metrics.put("avg_HTTP_client_request_duration", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_sum{exported_instance=~"user\\\\..*"}[5m]))
                            /
                            sum(rate(http_client_request_duration_seconds_count{exported_instance=~"user\\\\..*"}[5m]))
                            """));
            metrics.put("requestRate_RPS", queryAndExtract(
                    "sum(rate(http_client_request_duration_seconds_count{exported_instance=~\"user\\\\..*\"}[5m]))"));
            metrics.put("averageLatency", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_sum{exported_instance=~"user\\\\..*"}[5m]))
                            /\s
                            sum(rate(http_client_request_duration_seconds_count{exported_instance=~"user\\\\..*"}[5m]))"""));
            metrics.put("95PercentileLatency", queryAndExtract(
                    "histogram_quantile(0.95, sum(rate(http_client_request_duration_seconds_bucket{exported_instance=~\"user\\\\..*\"}[5m])) by (le))"));
            metrics.put("ErrorRate", queryAndExtract(
                    """
                            sum(rate(http_client_request_duration_seconds_count{exported_instance=~"user\\\\..*",http_response_status_code!~"2.."}[5m]))
                            /
                            sum(rate(http_client_request_duration_seconds_count{exported_instance=~"user\\\\..*"}[5m]))"""));
            metrics.put("TotalSpanCount", queryAndExtract(
                    "sum(increase(span_metrics_calls_total{namespace=\"user\", status_code!~\"STATUS_CODE_ERROR\"}[5m]))"
            ));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to query metrics", e);
            uiLogger.error("Failed to query metrics" + e.getMessage());
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        logger.info("Metrics: " + metrics);
        return metrics;
    }
}