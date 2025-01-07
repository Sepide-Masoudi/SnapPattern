package com.example.design_pattern_prototyping;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.stream.Stream;

public class WorkloadController {

    @FXML
    private ComboBox<String> fileDropdown;
    @FXML
    private ComboBox<String> workloadLevelComboBox;
    @FXML
    private TextField hostnameField;


    @FXML
    public void uploadFile() {
        System.out.println("Opening file chooser dialog...");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JMeter Files", "*.jmx"));

        File selectedFile = fileChooser.showOpenDialog(new Stage());

        if (selectedFile != null) {
            System.out.println("Uploading file: " + selectedFile.getAbsolutePath());

            try {
                Path targetDir = Paths.get("src/main/resources/workloads");
                Path targetPath = targetDir.resolve(selectedFile.getName());
                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("File uploaded successfully to: " + targetPath);
                showAlert("Success", "File uploaded successfully!", Alert.AlertType.INFORMATION);

                String fileName = selectedFile.getName();
                if (!fileDropdown.getItems().contains(fileName)) {
                    fileDropdown.getItems().add(fileName);
                }

            } catch (IOException e) {
                System.err.println("Error uploading file: " + e.getMessage());
                showAlert("Error", "Failed to upload the file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        } else {
            System.out.println("No file selected for upload.");
            showAlert("Error", "No file selected. Please choose a file to upload.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void initialize() {
        workloadLevelComboBox.getItems().addAll("Low", "Medium", "High");
        workloadLevelComboBox.setValue("Low");
        System.out.println("ComboBox initialized with default value: Low");

        Path workloadDir = Paths.get("src/main/resources/workloads");

        try (Stream<Path> stream = Files.list(workloadDir)) { // Use try-with-resources
            stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(fileName -> fileName.endsWith(".jmx"))
                    .forEach(fileDropdown.getItems()::add);

            if (!fileDropdown.getItems().isEmpty()) {
                fileDropdown.setValue(fileDropdown.getItems().get(0)); // Set the first file as default selection
            }
        } catch (IOException e) {
            showAlert("Error", "Failed to initialize file dropdown: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    public void runWorkload() {
        String selectedFileName = fileDropdown.getValue();

        if (selectedFileName != null && !selectedFileName.isEmpty()) {
            String hostname = hostnameField.getText();

            if (hostname == null || hostname.isEmpty()) {
                showAlert("Error", "Hostname cannot be empty. Please enter a valid hostname.", Alert.AlertType.WARNING);
                return;
            }

            Path filePath = Paths.get("src/main/resources/workloads", selectedFileName);
            File selectedFile = filePath.toFile();

            if (selectedFile.exists()) {
                System.out.println("Running workload with file: " + selectedFile.getAbsolutePath());
                String workloadLevel = workloadLevelComboBox.getValue();
                System.out.println("Selected workload level: " + workloadLevel);

                int numUsers, rampUp, duration;
                switch (workloadLevel) {
                    case "High":
                        numUsers = 200;
                        rampUp = 20;
                        duration = 120;
                        break;
                    case "Medium":
                        numUsers = 50;
                        rampUp = 10;
                        duration = 60;
                        break;
                    case "Low":
                    default:
                        numUsers = 10;
                        rampUp = 5;
                        duration = 30;
                        break;
                }
                System.out.println("Workload parameters - Users: " + numUsers + ", RampUp: " + rampUp + ", Duration: " + duration);

                Path jmeterPath = Paths.get("apache-jmeter-5.6.3/bin/ApacheJMeter.jar");
                File jmeterFile = jmeterPath.toFile();

                Path logDirectoryPath = Paths.get("src/main/resources/workloads");
                File logDirectory = logDirectoryPath.toFile();
                String logFilePath = new File(logDirectory, "workload_results" + workloadLevel.toLowerCase() + ".log").getAbsolutePath();

                if (!jmeterFile.exists()) {
                    System.out.println("JMeter JAR file not found: " + jmeterFile.getAbsolutePath());
                    showAlert("Error", "JMeter JAR file not found. Please check the path.", Alert.AlertType.ERROR);
                    return;
                }

                new Thread(() -> {
                    try {
                        ProcessBuilder processBuilder = new ProcessBuilder(
                                "java",
                                "-jar",
                                jmeterFile.getAbsolutePath(),
                                "-t", selectedFile.getAbsolutePath(),
                                "-Jhostname=" + hostname,
                                "-Jport=8080",
                                "-JnumUser=" + numUsers,
                                "-JrampUp=" + rampUp,
                                "-Jduration=" + duration,
                                "-l", logFilePath,
                                "-n"
                        );
                        processBuilder.redirectErrorStream(true);

                        Process process = processBuilder.start();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                        System.out.println("Process started. Waiting for it to complete...");

                        int exitCode = process.waitFor();
                        System.out.println("Process exited with code: " + exitCode);

                        Platform.runLater(() -> {
                            if (exitCode == 0) {
                                System.out.println("Workload executed successfully.");
                                showAlert("Success", "Workload executed successfully! Check workload_results_" + workloadLevel.toLowerCase() + ".log for details.", Alert.AlertType.INFORMATION);
                            } else {
                                System.out.println("Workload execution failed. Exit code: " + exitCode);
                                showAlert("Error", "Workload execution failed. Exit code: " + exitCode, Alert.AlertType.ERROR);
                            }
                        });
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Error during workload execution: " + e.getMessage());
                        Platform.runLater(() -> showAlert("Error", "Failed to execute workload: " + e.getMessage(), Alert.AlertType.ERROR));
                    }
                }).start();
            } else {
                System.out.println("Selected file does not exist: " + selectedFile.getAbsolutePath());
                showAlert("Error", "Selected file does not exist. Please select a valid file.", Alert.AlertType.ERROR);
            }
        }
    }

    private void showAlert(String title, String message, Alert.AlertType alertType) {
        System.out.println("Showing alert - " + title + ": " + message);
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
