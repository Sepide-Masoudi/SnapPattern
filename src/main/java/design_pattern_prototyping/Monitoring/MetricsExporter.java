package design_pattern_prototyping.Monitoring;

import design_pattern_prototyping.util.UILogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsExporter {

    private static final Logger logger = Logger.getLogger(MetricsExporter.class.getName());
    public UILogger uiLogger;

    public void setLogger(UILogger logger) {
        this.uiLogger = logger;
    }

    public void runMetricsService() {
        try {
            URL url = new URL("http://127.0.0.1:5000/generate_metrics");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(new byte[0]);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("Successfully triggered Python metrics generation service.");
                uiLogger.info("Successfully triggered Python metrics generation service.");
            } else {
                logger.warning("Failed to trigger Python metrics service. HTTP Code: " + responseCode);
                uiLogger.warning("Failed to trigger Python metrics service. HTTP Code: " + responseCode);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error calling Python metrics service: ", e);
            uiLogger.error("Error calling metrics service: " +e.getMessage());
        }
    }

    public void exportMetricsExcel(Map<String, String> metrics, String workload, String pattern) {
        String path = "Python/results";
        String fileName = path + "/metrics_data.xlsx";
        Path filePath = Paths.get(fileName);

        logger.info("Starting exportMetricsExcel method...");
        uiLogger.info("Starting exportMetricsExcel method...");
        uiLogger.info("File path: " + fileName);

        if (Files.exists(filePath)) {
            try {
                if (Files.size(filePath) == 0) {
                    Files.delete(filePath);
                    logger.warning("Deleted empty Excel file: " + fileName);
                } else {
                    logger.info("Excel file exists and is not empty.");
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to check or delete empty file: " + fileName, e);
            }
        } else {
            logger.info("Excel file does not exist. A new file will be created.");
        }

        try (Workbook workbook = (Files.exists(filePath)
                ? new XSSFWorkbook(Files.newInputStream(filePath))
                : new XSSFWorkbook());
             FileOutputStream outputStream = new FileOutputStream(fileName)) {

            logger.info("Workbook and FileOutputStream created successfully.");

            Sheet sheet = workbook.getSheet("Metrics");
            if (sheet == null) {
                logger.info("Metrics sheet does not exist. Creating new sheet...");
                sheet = workbook.createSheet("Metrics");

                // Create header row
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Pattern");
                headerRow.createCell(1).setCellValue("Workload Level");
                headerRow.createCell(2).setCellValue("Timestamp");
                headerRow.createCell(3).setCellValue("ContainerJoulesTotal");
                headerRow.createCell(4).setCellValue("ContainerPowerWattsAvg");
                headerRow.createCell(5).setCellValue("ContainerCacheMissAvg");
                headerRow.createCell(6).setCellValue("ContainerCpuCyclesAvg");
                headerRow.createCell(7).setCellValue("ContainerCpuInstructionsAvg");
                headerRow.createCell(8).setCellValue("PPW");
                headerRow.createCell(9).setCellValue("RW");
                headerRow.createCell(10).setCellValue("IPC");
                headerRow.createCell(11).setCellValue("MeanLatency");
                headerRow.createCell(12).setCellValue("95PercentileLatency");
                headerRow.createCell(13).setCellValue("RequestRate");
                headerRow.createCell(14).setCellValue("TotalRequests");
                headerRow.createCell(15).setCellValue("TotalSpans");
                headerRow.createCell(16).setCellValue("SpansPerRequest");

                logger.info("Header row created successfully.");
            } else {
                logger.info("Metrics sheet already exists.");
            }

            int nextRowNum = sheet.getLastRowNum() + 1;
            if (sheet.getRow(nextRowNum) != null) {
                nextRowNum++;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> flatMetrics = new HashMap<>();

            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                String metricName = entry.getKey();
                String jsonResponse = entry.getValue();

                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                if ("success".equals(rootNode.path("status").asText())) {
                    JsonNode results = rootNode.path("data").path("result");
                    if (results.isArray() && results.size() > 0) {
                        String value = results.get(0).path("value").get(1).asText();
                        flatMetrics.put(metricName, value);
                    }
                } else {
                    logger.warning("Metric fetch failed for: " + metricName + ". Status: " + rootNode.path("status").asText());
                    uiLogger.warning("Metric fetch failed for: " + metricName + ". Status: " + rootNode.path("status").asText());
                }
            }

            // Derived metrics with safe parsing
            double watts = parseMetrics("ContainerPowerWattsAvg", flatMetrics);
            double cycles = parseMetrics("ContainerCpuCyclesAvg", flatMetrics);
            double instructions = parseMetrics("ContainerCpuInstructionsAvg", flatMetrics);
            double spans = parseMetrics("TotalSpans", flatMetrics);
            double requests = parseMetrics("TotalRequests", flatMetrics);

            // PPW: instructions per watt
            String ppw = (!Double.isNaN(watts) && watts != 0) ? String.valueOf(instructions / watts) : "NULL";
            // RW: Requests per Watts
            String rw = (!Double.isNaN(watts) && watts != 0) ? String.valueOf(requests / watts) : "NULL";
            // IPC: instructions per cycle
            String ipc = (!Double.isNaN(cycles) && cycles != 0) ? String.valueOf(instructions / cycles) : "NULL";
            // Spans per request
            String spansReq = (!Double.isNaN(requests) && requests != 0) ? String.valueOf(spans / requests) : "NULL";

            flatMetrics.put("PPW", ppw);
            flatMetrics.put("RW", rw);
            flatMetrics.put("IPC", ipc);
            flatMetrics.put("SpansPerRequest", spansReq);

            logger.info("Computed PPW (instructions per watt): " + ppw);
            logger.info("Computed PPJ (instructions per joule): " + rw);
            logger.info("Computed IPC (instructions per cycle): " + ipc);
            logger.info("Computed Spans per Request: " + spansReq);


            // Write the aggregated metrics to Excel
            Row valuesRow = sheet.createRow(nextRowNum++);
            valuesRow.createCell(0).setCellValue(pattern);
            valuesRow.createCell(1).setCellValue(workload);
            valuesRow.createCell(2).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            valuesRow.createCell(3).setCellValue(flatMetrics.getOrDefault("ContainerJoulesTotal", "NULL"));
            valuesRow.createCell(4).setCellValue(flatMetrics.getOrDefault("ContainerPowerWattsAvg", "NULL"));
            valuesRow.createCell(5).setCellValue(flatMetrics.getOrDefault("ContainerCacheMissAvg", "NULL"));
            valuesRow.createCell(6).setCellValue(flatMetrics.getOrDefault("ContainerCpuCyclesAvg", "NULL"));
            valuesRow.createCell(7).setCellValue(flatMetrics.getOrDefault("ContainerCpuInstructionsAvg", "NULL"));
            valuesRow.createCell(8).setCellValue(flatMetrics.getOrDefault("PPW", "NULL"));
            valuesRow.createCell(9).setCellValue(flatMetrics.getOrDefault("RW", "NULL"));
            valuesRow.createCell(10).setCellValue(flatMetrics.getOrDefault("IPC", "NULL"));
            valuesRow.createCell(11).setCellValue(flatMetrics.getOrDefault("MeanLatency", "NULL"));
            valuesRow.createCell(12).setCellValue(flatMetrics.getOrDefault("95PercentileLatency", "NULL"));
            valuesRow.createCell(13).setCellValue(flatMetrics.getOrDefault("RequestRate", "NULL"));
            valuesRow.createCell(14).setCellValue(flatMetrics.getOrDefault("TotalRequests", "NULL"));
            valuesRow.createCell(15).setCellValue(flatMetrics.getOrDefault("TotalSpans", "NULL"));
            valuesRow.createCell(16).setCellValue(flatMetrics.getOrDefault("SpansPerRequest", "NULL"));

            // Auto-size all columns
            for (int i = 0; i <= 15; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            logger.info("Metrics exported to " + fileName);
            uiLogger.info("Metrics exported to " + fileName);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to export metrics: ", e);
            uiLogger.error("Failed to export metrics: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during exportMetricsExcel: ", e);
            uiLogger.error("Unexpected error during exportMetricsExcel: " + e.getMessage());
        }

        logger.info("Finished exportMetricsExcel method.");
        uiLogger.info("Finished exportMetricsExcel method.");
    }

    public void exportEnergyTimeSeriesExcel(String json, String workload, String pattern) {
        String path = "Python/results";
        String fileName = path + "/metrics_data.xlsx";

        try (Workbook workbook = Files.exists(Paths.get(fileName))
                ? new XSSFWorkbook(Files.newInputStream(Paths.get(fileName)))
                : new XSSFWorkbook();
             FileOutputStream outputStream = new FileOutputStream(fileName)) {

            Sheet sheet = workbook.getSheet("EnergyTimeSeries");
            if (sheet == null) {
                sheet = workbook.createSheet("EnergyTimeSeries");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("Pattern");
                header.createCell(1).setCellValue("Workload");
                header.createCell(2).setCellValue("RunID");
                header.createCell(3).setCellValue("StepIndex");
                header.createCell(4).setCellValue("Timestamp");
                header.createCell(5).setCellValue("Energy (Joules)");
            }

            String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode results = root.path("data").path("result");

            int rowNum = sheet.getLastRowNum() + 1;
            for (JsonNode series : results) {
                JsonNode values = series.path("values");
                int stepIndex = 0;
                for (JsonNode valuePair : values) {
                    double timestamp = valuePair.get(0).asDouble();
                    String valueStr = valuePair.get(1).asText();

                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(pattern);
                    row.createCell(1).setCellValue(workload);
                    row.createCell(2).setCellValue(runId);
                    row.createCell(3).setCellValue(stepIndex);
                    row.createCell(4).setCellValue(timestamp);
                    row.createCell(5).setCellValue(Double.parseDouble(valueStr));

                    stepIndex++;
                }
            }

            for (int i = 0; i <= 5; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            logger.info("Energy time series exported to " + fileName);
            uiLogger.info("Energy time series exported to " + fileName);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to export energy time series", e);
            uiLogger.error("Failed to export energy time series: " + e.getMessage());
        }
    }

    private double parseMetrics(String name, Map<String, String> flatMetrics) {
        String val = flatMetrics.getOrDefault(name, "NULL");
        try {
            return Double.parseDouble(val);
        } catch (Exception e) {
            logger.warning("Invalid or missing value for " + name + ": " + val);
            return Double.NaN;
        }
    }

}

