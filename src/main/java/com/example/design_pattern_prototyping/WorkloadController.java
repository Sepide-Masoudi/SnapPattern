package com.example.design_pattern_prototyping;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class WorkloadController {

    private static final Logger logger = Logger.getLogger(WorkloadController.class.getName());

    @FXML
    private ComboBox<String> fileDropdown;
    @FXML
    private ComboBox<String> workloadLevelComboBox;
    @FXML
    private TextField hostnameField;
    @FXML
    private TextField portField;
    @FXML
    private Button abortButton;
    private Process currentProcess = null;
    private final AtomicBoolean isAborted = new AtomicBoolean(false);
    private final AtomicBoolean timeoutTriggered = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @FXML
    public void initialize() {
        // Register this controller with the mediator
        ControllerMediatorImpl.getInstance().registerWorkloadController(this);

        workloadLevelComboBox.getItems().addAll("Low", "Medium", "High");
        workloadLevelComboBox.setValue("Low");
        logger.info("ComboBox initialized with default value: Low");

        Path workloadDir = Paths.get("src/main/resources/workloads");

        try (Stream<Path> stream = Files.list(workloadDir)) {
            stream.filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(fileName -> fileName.endsWith(".jmx"))
                    .forEach(fileDropdown.getItems()::add);

            if (!fileDropdown.getItems().isEmpty()) {
                fileDropdown.setValue(fileDropdown.getItems().get(0));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize file dropdown", e);
            showAlert("Error", "Failed to initialize file dropdown: " + e.getMessage(), Alert.AlertType.ERROR);
        }
        abortButton.setDisable(true);
        logger.info("WorkloadController initialized.");
    }

    @FXML
    public void uploadFile() {
        logger.info("Opening file chooser dialog...");
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JMeter Files", "*.jmx"));
        File selectedFile = fileChooser.showOpenDialog(new Stage());

        if (selectedFile != null) {
            logger.info("Uploading file: " + selectedFile.getAbsolutePath());

            try {
                Path targetDir = Paths.get("src/main/resources/workloads");
                Path targetPath = targetDir.resolve(selectedFile.getName());
                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                logger.info("File uploaded successfully to: " + targetPath);
                showAlert("Success", "File uploaded successfully!", Alert.AlertType.INFORMATION);

                String fileName = selectedFile.getName();
                if (!fileDropdown.getItems().contains(fileName)) {
                    fileDropdown.getItems().add(fileName);
                }

            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error uploading file", e);
                showAlert("Error", "Failed to upload the file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        } else {
            logger.warning("No file selected for upload.");
            showAlert("Error", "No file selected. Please choose a file to upload.", Alert.AlertType.WARNING);
        }
    }

    @FXML
    public void runWorkload() {
        String selectedFileName = fileDropdown.getValue();
        if (selectedFileName != null && !selectedFileName.isEmpty()) {
            String hostname = hostnameField.getText();
            String port = portField.getText();
            if (hostname == null || hostname.isEmpty()) {
                showAlert("Error", "Hostname cannot be empty. Please enter a valid hostname.", Alert.AlertType.WARNING);
                return;
            }

            Path filePath = Paths.get("src/main/resources/workloads", selectedFileName);
            File selectedFile = filePath.toFile();

            if (selectedFile.exists()) {
                logger.info("Running workload with file: " + selectedFile.getAbsolutePath());
                String workloadLevel = workloadLevelComboBox.getValue();
                logger.info("Selected workload level: " + workloadLevel);

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
                logger.info("Workload parameters - Users: " + numUsers + ", RampUp: " + rampUp + ", Duration: " + duration);

                Path jmeterPath = Paths.get("apache-jmeter-5.6.3/bin/ApacheJMeter.jar");
                File jmeterFile = jmeterPath.toFile();

                //Path logDirectoryPath = Paths.get("src/main/resources/workloads");
                //File logDirectory = logDirectoryPath.toFile();
                //String logFilePath = new File(logDirectory, "workload_results" + workloadLevel.toLowerCase() + ".log").getAbsolutePath();

                if (!jmeterFile.exists()) {
                    logger.severe("JMeter JAR file not found: " + jmeterFile.getAbsolutePath());
                    showAlert("Error", "JMeter JAR file not found. Please check the path.", Alert.AlertType.ERROR);
                    return;
                }

                isAborted.set(false);
                abortButton.setDisable(false);

                new Thread(() -> {
                    try {
                        ProcessBuilder processBuilder = new ProcessBuilder(
                                "java",
                                "-jar",
                                jmeterFile.getAbsolutePath(),
                                "-t", selectedFile.getAbsolutePath(),
                                "-Jhostname=" + hostname,
                                "-Jport=" + port,
                                "-JnumUser=" + numUsers,
                                "-JrampUp=" + rampUp,
                                "-Jduration=" + duration,
                                //"-l", logFilePath,
                                "-n"
                        );
                        processBuilder.redirectErrorStream(true);
                        currentProcess = processBuilder.start();

                        // Schedule task to stop the process after 10 minutes
                        scheduler.schedule(() -> {
                            if (currentProcess != null && currentProcess.isAlive()) {
                                timeoutTriggered.set(true);
                                logger.warning("Stopping workload after 10 minutes...");
                                try {
                                    ProcessBuilder stopBuilder = new ProcessBuilder("./stoptest.sh");
                                    stopBuilder.directory(new File("apache-jmeter-5.6.3/bin"));
                                    stopBuilder.redirectErrorStream(true);
                                    Process stopProcess = stopBuilder.start();

                                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(stopProcess.getInputStream()))) {
                                        String line;
                                        while ((line = reader.readLine()) != null) {
                                            logger.info("[stoptest.sh] " + line);
                                        }
                                    }

                                    stopProcess.waitFor();
                                } catch (IOException | InterruptedException e) {
                                    logger.log(Level.SEVERE, "Failed to execute stoptest.sh", e);
                                }

                                Platform.runLater(() -> showAlert("Info", "Workload stopped after 10 minutes.", Alert.AlertType.INFORMATION));
                                abortButton.setDisable(true);
                            }
                        }, 10, TimeUnit.MINUTES);

                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                logger.info(line);
                            }
                        }

                        int exitCode = currentProcess.waitFor();
                        currentProcess = null;

                        Platform.runLater(() -> {
                            abortButton.setDisable(true);

                            if (isAborted.get()) {
                                logger.info("Workload was aborted by user.");
                                showAlert("Info", "Workload was aborted by the user.", Alert.AlertType.INFORMATION);
                            } else {
                                ControllerMediator mediator = ControllerMediatorImpl.getInstance();
                                mediator.getMetricsController().generateMetrics();

                                if (exitCode == 0) {
                                    if (timeoutTriggered.get()) {
                                        logger.info("Workload stopped by timeout.");
                                        showAlert("Info", "Workload stopped automatically after timeout! Check workload_results_" + workloadLevel.toLowerCase() + ".log for details.", Alert.AlertType.INFORMATION);
                                    } else {
                                        logger.info("Workload executed successfully.");
                                        showAlert("Success", "Workload executed successfully! Check workload_results_" + workloadLevel.toLowerCase() + ".log for details.", Alert.AlertType.INFORMATION);
                                    }
                                } else {
                                    logger.warning("Workload execution failed. Exit code: " + exitCode);
                                    showAlert("Error", "Workload execution failed. Exit code: " + exitCode, Alert.AlertType.ERROR);
                                }
                            }
                        });
                    } catch (IOException | InterruptedException e) {
                        logger.log(Level.SEVERE, "Error during workload execution", e);
                        Platform.runLater(() -> showAlert("Error", "Failed to execute workload: " + e.getMessage(), Alert.AlertType.ERROR));
                    }
                }).start();
            } else {
                logger.warning("Selected file does not exist: " + selectedFile.getAbsolutePath());
                showAlert("Error", "Selected file does not exist. Please select a valid file.", Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    public void abortWorkload() {
        if (currentProcess != null && currentProcess.isAlive()) {
            try {
                logger.info("Aborting workload using stoptest.sh...");
                ProcessBuilder stopBuilder = new ProcessBuilder("./stoptest.sh");
                stopBuilder.directory(new File("apache-jmeter-5.6.3/bin"));
                stopBuilder.redirectErrorStream(true);
                Process stopProcess = stopBuilder.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stopProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[stoptest.sh] " + line);
                    }
                }

                int exitCode = stopProcess.waitFor();
                logger.info("stoptest.sh exited with code: " + exitCode);

                isAborted.set(true);
                abortButton.setDisable(true);
                showAlert("Aborted", "Workload execution aborted successfully!", Alert.AlertType.INFORMATION);
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "Failed to run stoptest.sh", e);
                showAlert("Error", "Failed to run stoptest.sh: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    public String getSelectedWorkloadLevel() {
        return workloadLevelComboBox.getValue();
    }

    private void showAlert(String title, String message, Alert.AlertType alertType) {
        logger.info("Showing alert - " + title + ": " + message);
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}