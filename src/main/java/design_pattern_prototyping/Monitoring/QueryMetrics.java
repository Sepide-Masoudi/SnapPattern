package design_pattern_prototyping.Monitoring;

import design_pattern_prototyping.controller.ControllerMediator;
import design_pattern_prototyping.controller.ControllerMediatorImpl;
import design_pattern_prototyping.util.UILogger;
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
    private final ControllerMediator mediator;

    public QueryMetrics() {
        this.prometheusClient = new PrometheusClient();
        this.mediator = ControllerMediatorImpl.getInstance();
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
            String frontendServiceName = mediator.getSelectedUserService();
            uiLogger.info( "Frontend: " +frontendServiceName);

            // Kepler metrics
            metrics.put("ContainerJoulesTotal", queryAndExtract(
                    "sum(increase(kepler_container_joules_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("ContainerPowerWattsAvg", queryAndExtract(
                    "sum(rate(kepler_container_joules_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("ContainerCpuCyclesAvg", queryAndExtract(
                    "sum(rate(kepler_container_cpu_cycles_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("ContainerCacheMissAvg", queryAndExtract(
                    "sum(rate(kepler_container_cache_miss_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)\n"));
            metrics.put("ContainerCpuInstructionsAvg", queryAndExtract(
                    "sum(rate(kepler_container_cpu_instructions_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)"));
            metrics.put("ContainerCpuInstructionsTotal", queryAndExtract(
                    "sum(increase(kepler_container_cpu_instructions_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)"));

            // Span metrics
            metrics.put("MeanLatency", queryAndExtract(
                    """
                            sum(rate(span_metrics_duration_milliseconds_sum{namespace="user|pattern"}[5m]))
                            /
                            sum(rate(span_metrics_duration_milliseconds_count{namespace="user|pattern"}[5m]))
                            """));
            metrics.put("95PercentileLatency", queryAndExtract(
                    "histogram_quantile(0.95, sum(rate(span_metrics_duration_milliseconds_bucket{namespace=\"user|pattern\"}[5m])) by (le))"));
            metrics.put("RequestRate", queryAndExtract(
                    String.format("sum(rate(span_metrics_calls_total{service_name=\"%s\",span_kind=\"SPAN_KIND_SERVER\", status_code!~\"STATUS_CODE_ERROR\"}[5m]))", frontendServiceName)));
            metrics.put("TotalRequests", queryAndExtract(
                    String.format("sum(increase(span_metrics_calls_total{service_name=\"%s\",span_kind=\"SPAN_KIND_SERVER\", status_code!~\"STATUS_CODE_ERROR\"}[5m]))", frontendServiceName)));
            metrics.put("TotalSpans", queryAndExtract(
                    "sum(increase(span_metrics_calls_total{namespace=~\"user|pattern\",status_code!~\"STATUS_CODE_ERROR\"}[5m]))"));
            //metrics.put("TotalRequestError", queryAndExtract(
            //        "sum(increase(span_metrics_calls_total{service_name=\"teastore-webui\", status_code=\"STATUS_CODE_ERROR\"}[10m]))"));

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to query metrics", e);
            uiLogger.error("Failed to query metrics" + e.getMessage());
            metrics.put("error", "Failed to query metrics: " + e.getMessage());
        }
        logger.info("Metrics: " + metrics);
        return metrics;
    }

    public String queryEnergyTimeSeries() throws Exception {
        String promql = "sum(increase(kepler_container_joules_total{container_namespace=~\"user|pattern\"}[5m])) by (container_namespace)";
        logger.info("Querying energy time series...");
        uiLogger.info("Querying energy time series...");

        String start = java.time.Instant.now().minus(java.time.Duration.ofMinutes(5)).toString();
        String end = java.time.Instant.now().toString();
        String step = "10s";

        String response = prometheusClient.queryRange(promql, start, end, step);

        logger.info("Energy time series response: " + response);
        uiLogger.info("Energy time series response: " + response);

        return response;
    }
}