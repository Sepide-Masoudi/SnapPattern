package com.example.design_pattern_prototyping;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class Main extends Application {

    private static Process pyProcess;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the JavaFX UI
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("Main.fxml")));
        primaryStage.setTitle("Local Kubernetes Microservice Design Pattern Prototype");
        primaryStage.setScene(new Scene(root, 800, 600));
        primaryStage.show();

        // Ensure Python service is stopped when the application closes
        primaryStage.setOnCloseRequest(event -> {
            if (pyProcess != null && pyProcess.isAlive()) {
                pyProcess.destroy();
                System.out.println("Python service stopped.");
            }
        });
    }

    public static void main(String[] args) {
        // Start the Python metrics service
        startMetricsService();

        // Launch the JavaFX application
        launch(args);
    }

    private static void startMetricsService() {
        try {
            // Path to Python executable and Python service file
            String pythonPath = "Python/venv/Scripts/python.exe";
            String flaskAppPath = "Python/Metrics_Service.py";

            // Start the metrics service using ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(pythonPath, flaskAppPath);
            processBuilder.redirectErrorStream(true); // Redirect error stream to standard output
            pyProcess = processBuilder.start();

            System.out.println("Metrics service started.");

            // Output of the Metrics service
            /**
             * new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(pyProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[Flask] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading Metrics service output: " + e.getMessage());
                }
            }).start();
             **/

        } catch (IOException e) {
            System.err.println("Failed to start Metrics service: " + e.getMessage());
        }
    }
}
