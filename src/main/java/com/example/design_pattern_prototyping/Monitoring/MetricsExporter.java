package com.example.design_pattern_prototyping.Monitoring;

import com.example.design_pattern_prototyping.util.UILogger;
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
import java.util.Map;
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
        String fileName = path + "/metrics_agg.xlsx";
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
                headerRow.createCell(3).setCellValue("containerJoulesTotal");
                headerRow.createCell(4).setCellValue("containerCacheMissTotal");
                headerRow.createCell(5).setCellValue("containerCpuCyclesTotal");
                headerRow.createCell(6).setCellValue("containerCpuInstructions");
                headerRow.createCell(7).setCellValue("energyEfficiency");
                headerRow.createCell(8).setCellValue("IPC");
                headerRow.createCell(9).setCellValue("requestRate_RPS");
                headerRow.createCell(10).setCellValue("avg_HTTP_client_request_duration");
                headerRow.createCell(11).setCellValue("95PercentileLatency");
                headerRow.createCell(12).setCellValue("TotalSpanCount");
                //headerRow.createCell(13).setCellValue("ErrorRate");

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

            // Compute Energy Efficiency and IPC
            try {
                double joules = Double.parseDouble(flatMetrics.getOrDefault("containerJoulesTotal", "NULL"));
                double cycles = Double.parseDouble(flatMetrics.getOrDefault("containerCpuCyclesTotal", "NULL"));
                double instructions = Double.parseDouble(flatMetrics.getOrDefault("containerCpuInstructions", "NULL"));

                // Energy Efficiency: instructions per joule
                String energyEfficiency = (joules != 0) ? String.valueOf(instructions / joules) : "NULL";

                // IPC: instructions per cycle
                String ipc = (cycles != 0) ? String.valueOf(instructions / cycles) : "NULL";

                flatMetrics.put("energyEfficiency", energyEfficiency);
                flatMetrics.put("IPC", ipc);

                logger.info("Computed Energy Efficiency (instructions per joule): " + energyEfficiency);
                logger.info("Computed IPC (instructions per cycle): " + ipc);
            } catch (Exception e) {
                logger.warning("Failed to compute derived metrics: " + e.getMessage());
                flatMetrics.put("energyEfficiency", "NULL");
                flatMetrics.put("IPC", "NULL");
            }


            // Write the aggregated metrics to Excel
            Row valuesRow = sheet.createRow(nextRowNum++);
            valuesRow.createCell(0).setCellValue(pattern);
            valuesRow.createCell(1).setCellValue(workload);
            valuesRow.createCell(2).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            valuesRow.createCell(3).setCellValue(flatMetrics.getOrDefault("containerJoulesTotal", "NULL"));
            valuesRow.createCell(4).setCellValue(flatMetrics.getOrDefault("containerCacheMissTotal", "NULL"));
            valuesRow.createCell(5).setCellValue(flatMetrics.getOrDefault("containerCpuCyclesTotal", "NULL"));
            valuesRow.createCell(6).setCellValue(flatMetrics.getOrDefault("containerCpuInstructions", "NULL"));
            valuesRow.createCell(7).setCellValue(flatMetrics.getOrDefault("energyEfficiency", "NULL"));
            valuesRow.createCell(8).setCellValue(flatMetrics.getOrDefault("IPC", "NULL"));
            valuesRow.createCell(9).setCellValue(flatMetrics.getOrDefault("requestRate_RPS", "NULL"));
            valuesRow.createCell(10).setCellValue(flatMetrics.getOrDefault("avg_HTTP_client_request_duration", "NULL"));
            valuesRow.createCell(11).setCellValue(flatMetrics.getOrDefault("95PercentileLatency", "NULL"));
            valuesRow.createCell(12).setCellValue(flatMetrics.getOrDefault("TotalSpanCount", "NULL"));
            //valuesRow.createCell(13).setCellValue(flatMetrics.getOrDefault("ErrorRate", "NULL"));

            // Auto-size all columns
            for (int i = 0; i <= 13; i++) {
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
}

