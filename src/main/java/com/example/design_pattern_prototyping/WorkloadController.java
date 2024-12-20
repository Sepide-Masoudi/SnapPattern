package com.example.design_pattern_prototyping;

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

public class WorkloadController {

    @FXML
    private TextField filePathField; // TextField to display file path
    @FXML
    private ComboBox<String> workloadLevelComboBox;
    private File selectedFile; // To hold the reference to the selected file

    @FXML
    public void handleFileUpload() {
        System.out.println("Opening file chooser dialog...");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JMeter Files", "*.jmx"));

        selectedFile = fileChooser.showOpenDialog(new Stage());

        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
            System.out.println("File selected: " + selectedFile.getAbsolutePath());
        } else {
            filePathField.clear();
            System.out.println("No file selected.");
        }
    }

    @FXML
    public void uploadFile() {
        if (selectedFile != null) {
            System.out.println("Uploading file: " + selectedFile.getAbsolutePath());
            try {
                Path targetDir = Paths.get("src/main/resources/workloads");
                System.out.println("Target directory: " + targetDir);

                if (Files.notExists(targetDir)) {
                    Files.createDirectories(targetDir);
                    System.out.println("Directory created: " + targetDir);
                }

                Path targetPath = targetDir.resolve(selectedFile.getName());
                System.out.println("Copying file to: " + targetPath);

                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                System.out.println("File upload successful!");
                showAlert("Success", "File uploaded successfully!", Alert.AlertType.INFORMATION);
            } catch (IOException e) {
                System.out.println("Error uploading file: " + e.getMessage());
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
    }

    @FXML
    public void runWorkload() {
        if (selectedFile != null) {
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

            String jmeterPath = "C:\\Users\\markh\\Documents\\4_Master\\Design_Pattern_Prototyping\\apache-jmeter-5.6.3\\bin\\ApacheJMeter.jar";
            File jmeterFile = new File(jmeterPath);

            String logDirectoryPath = "C:\\Users\\markh\\Documents\\4_Master\\Design_Pattern_Prototyping\\src\\main\\resources\\workloads";
            File logDirectory = new File(logDirectoryPath);
            String logFilePath = new File(logDirectory, "workload_results_" + workloadLevel.toLowerCase() + ".log").getAbsolutePath();


            if (!jmeterFile.exists()) {
                System.out.println("JMeter JAR file not found: " + jmeterFile.getAbsolutePath());
                showAlert("Error", "JMeter JAR file not found. Please check the path.", Alert.AlertType.ERROR);
                return;
            }

            try {
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "java",
                        "-jar",
                        jmeterFile.getAbsolutePath(), // Use the dynamic JMeter path
                        "-t", selectedFile.getAbsolutePath(),
                        "-Jhostname=10.1.1.1",
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

                if (exitCode == 0) {
                    System.out.println("Workload executed successfully.");
                    showAlert("Success", "Workload executed successfully! Check workload_results_" + workloadLevel.toLowerCase() + ".log for details.", Alert.AlertType.INFORMATION);
                } else {
                    System.out.println("Workload execution failed. Exit code: " + exitCode);
                    showAlert("Error", "Workload execution failed. Exit code: " + exitCode, Alert.AlertType.ERROR);
                }
            } catch (IOException | InterruptedException e) {
                System.out.println("Error during workload execution: " + e.getMessage());
                showAlert("Error", "Failed to execute workload: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        } else {
            System.out.println("No file selected to run workload.");
            showAlert("Error", "No file selected. Please upload a workload file to run.", Alert.AlertType.WARNING);
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
