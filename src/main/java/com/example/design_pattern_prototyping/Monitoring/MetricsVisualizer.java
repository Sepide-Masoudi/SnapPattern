package com.example.design_pattern_prototyping.Monitoring;

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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

// TODO Recieve Metric Plots back from the Python service to display in Window
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
        try (Workbook workbook = (Files.exists(filePath)
                ? new XSSFWorkbook(Files.newInputStream(filePath))
                : new XSSFWorkbook());
             FileOutputStream outputStream = new FileOutputStream(fileName)) {

            Sheet sheet = workbook.getSheet("Metrics");
            if (sheet == null) {
                sheet = workbook.createSheet("Metrics");

                // Create header row
                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Pattern");
                headerRow.createCell(1).setCellValue("Workload Level");
                headerRow.createCell(2).setCellValue("Timestamp");

                // Add metric names to the header row dynamically
                int columnIndex = 3;
                for (String metricName : metrics.keySet()) {
                    headerRow.createCell(columnIndex++).setCellValue(metricName);
                }
            }

            // Find the next empty row
            int nextRowNum = sheet.getLastRowNum() + 1;
            if (sheet.getRow(nextRowNum) != null) {
                nextRowNum++;
            }
            Row valuesRow = sheet.createRow(nextRowNum);

            valuesRow.createCell(0).setCellValue(pattern); // Selected pattern implementation
            valuesRow.createCell(1).setCellValue(workload); // Selected workload level
            valuesRow.createCell(2).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            int columnIndex = 3;
            for (String metricValue : metrics.values()) {
                valuesRow.createCell(columnIndex++).setCellValue(metricValue);
            }

            // Auto-size columns
            for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            logger.info("Metrics exported to " + fileName);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to export metrics: " + e);
        }
    }
}

