package com.example.design_pattern_prototyping.Monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MetricsVisualizer {

    private static final Logger logger = Logger.getLogger(MetricsVisualizer.class.getName());

    public static void sendMetricsToPython(String excelFilePath) {
        try {
            URL url = new URL("http://127.0.0.1:5000/generate_metrics");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; utf-8");
            connection.setDoOutput(true);

            // Create JSON payload with the file path
            String jsonPayload = String.format("{\"file_path\": \"%s\"}", excelFilePath.replace("\\", "/"));

            // Write JSON payload to request body
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                logger.info("File path sent to Python service successfully.");
            } else {
                logger.warning("Failed to send file path to Python service. HTTP Code: " + responseCode);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send file path to Python service: " + e);
        }
    }

    public static void exportMetricsExcel(Map<String, String> metrics, String workload, String pattern) {
        String path = "Python/results";
        String fileName = path + "/metrics.xlsx";
        Path filePath = Paths.get(fileName);

        logger.info("Starting exportMetricsExcel method...");
        logger.info("File path: " + fileName);

        // Check if the file exists and is empty. If so, delete it.
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
                headerRow.createCell(3).setCellValue("Container Name");
                headerRow.createCell(4).setCellValue("containerJoulesTotal");
                headerRow.createCell(5).setCellValue("containerCacheMissTotal");
                headerRow.createCell(6).setCellValue("containerCpuCyclesTotal");
                headerRow.createCell(7).setCellValue("containerCpuInstructions");
                headerRow.createCell(8).setCellValue("energyEfficiency");
                headerRow.createCell(9).setCellValue("avg_HTTP_client_request_duration");
                headerRow.createCell(10).setCellValue("requestRate_RPS");
                headerRow.createCell(11).setCellValue("averageLatency");
                headerRow.createCell(12).setCellValue("95PercentileLatency");
                headerRow.createCell(13).setCellValue("ErrorRate");

                logger.info("Header row created successfully.");
            } else {
                logger.info("Metrics sheet already exists.");
            }

            int nextRowNum = sheet.getLastRowNum() + 1;
            if (sheet.getRow(nextRowNum) != null) {
                nextRowNum++;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Map<String, String>> containerMetrics = new HashMap<>();

            // Parse each metric JSON and populate the containerMetrics map
            for (Map.Entry<String, String> entry : metrics.entrySet()) {
                String metricName = entry.getKey();
                String jsonResponse = entry.getValue();

                JsonNode rootNode = objectMapper.readTree(jsonResponse);
                if ("success".equals(rootNode.path("status").asText())) {
                    JsonNode results = rootNode.path("data").path("result");
                    for (JsonNode node : results) {

                        String containerName;
                        JsonNode metricNode = node.path("metric");

                        if (metricNode.has("container_name")) {
                            // kepler metrics format
                            containerName = metricNode.path("container_name").asText();
                        } else if (metricNode.has("exported_job")) {
                            // spanmetrics format
                            containerName = metricNode.path("exported_job").asText();
                        } else {
                            containerName = "unknown";
                        }
                        String value = node.path("value").get(1).asText();
                        // Store metric value per container
                        containerMetrics.computeIfAbsent(containerName, k -> new HashMap<>()).put(metricName, value);
                    }
                } else {
                    logger.warning("Metric fetch failed for: " + metricName + ". Status: " + rootNode.path("status").asText());
                }
            }

            // Write the aggregated metrics to Excel
            for (Map.Entry<String, Map<String, String>> entry : containerMetrics.entrySet()) {
                String containerName = entry.getKey();
                Map<String, String> metricValues = entry.getValue();

                Row valuesRow = sheet.createRow(nextRowNum++);
                valuesRow.createCell(0).setCellValue(pattern);
                valuesRow.createCell(1).setCellValue(workload);
                valuesRow.createCell(2).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                valuesRow.createCell(3).setCellValue(containerName);
                valuesRow.createCell(4).setCellValue(metricValues.getOrDefault("containerJoulesTotal", "NULL"));
                valuesRow.createCell(5).setCellValue(metricValues.getOrDefault("containerCacheMissTotal", "NULL"));
                valuesRow.createCell(6).setCellValue(metricValues.getOrDefault("containerCpuCyclesTotal", "NULL"));
                valuesRow.createCell(7).setCellValue(metricValues.getOrDefault("containerCpuInstructions", "NULL"));
                valuesRow.createCell(8).setCellValue(metricValues.getOrDefault("energyEfficiency", "NULL"));
                valuesRow.createCell(9).setCellValue(metricValues.getOrDefault("avg_HTTP_client_request_duration", "NULL"));
                valuesRow.createCell(10).setCellValue(metricValues.getOrDefault("requestRate_RPS", "NULL"));
                valuesRow.createCell(11).setCellValue(metricValues.getOrDefault("averageLatency", "NULL"));
                valuesRow.createCell(12).setCellValue(metricValues.getOrDefault("95PercentileLatency", "NULL"));
                valuesRow.createCell(13).setCellValue(metricValues.getOrDefault("ErrorRate", "NULL"));

                logger.info("Wrote metrics for container: " + containerName);
            }

            // Auto-size columns
            for (int i = 0; i <= 8; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            logger.info("Metrics exported to " + fileName);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to export metrics: " + e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during exportMetricsExcel: " + e);
        }

        logger.info("Finished exportMetricsExcel method.");
    }
}

