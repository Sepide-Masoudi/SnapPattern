package com.example.design_pattern_prototyping.Kubernetes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesDeployer {

    private static final Logger logger = Logger.getLogger(KubernetesDeployer.class.getName());

    public static boolean statusMinikube() {
        try {
            logger.info("Checking Minikube status...");
            ProcessBuilder builder = new ProcessBuilder("minikube", "status");
            Process process = builder.start();

            // Read the output of the command
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce("", (acc, line) -> acc + line + System.lineSeparator());
            }

            // Wait for the process to finish
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                logger.warning("Error: Minikube status command exited with code " + exitCode);
                return false;
            }

            // Check if the output contains "Running"
            boolean isRunning = output.contains("Running");
            logger.info("Minikube status:\n" + output);
            return isRunning;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check Minikube status.", e);
            return false;
        }
    }

    public static boolean startMinikube() {
        try {
            if (statusMinikube()) {
                logger.info("Minikube is already running.");
                return true;
            }
            logger.info("Starting Minikube...");
            ProcessBuilder startBuilder = new ProcessBuilder("minikube", "start");
            Process startProcess = startBuilder.start();

            Thread outputThread = new Thread(() -> {
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(startProcess.getInputStream()))) {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        logger.info("[START OUTPUT] " + line);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error reading start output", e);
                }
            });

            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(startProcess.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        logger.warning("[START ERROR] " + line);
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error reading start error output", e);
                }
            });

            outputThread.start();
            errorThread.start();

            startProcess.waitFor();
            outputThread.join();
            errorThread.join();

            int exitValue = startProcess.exitValue();
            if (exitValue != 0) {
                logger.severe("Critical error starting Minikube. Exit Code: " + exitValue);
                return false;
            }

            logger.info("Minikube started successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start Minikube.", e);
            return false;
        }
    }

    public static boolean stopMinikube() {
        try {
            logger.info("Stopping Minikube...");
            ProcessBuilder stopBuilder = new ProcessBuilder("minikube", "stop");
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor();

            logger.info("Minikube stopped successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to stop Minikube.", e);
            return false;
        }
    }

    public static void createNamespace() {
        try {
            logger.info("Creating namespace 'user'...");
            ProcessBuilder namespace = new ProcessBuilder("kubectl", "create", "namespace", "user");
            Process process = namespace.start();

            try (BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                 BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                boolean namespaceExists = false;
                while ((line = stdError.readLine()) != null) {
                    logger.warning("ERROR: " + line);
                    if (line.contains("already exists")) {
                        namespaceExists = true;
                    }
                }
                while ((line = stdOutput.readLine()) != null) {
                    logger.info("OUTPUT: " + line);
                }

                process.waitFor();

                if (!namespaceExists) {
                    logger.info("Namespace 'user' created successfully.");
                } else {
                    logger.info("Namespace 'user' already exists.");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create namespace 'user'.", e);
        }
    }

    public static void deleteUserNamespace() {
        try {
            logger.info("Deleting namespace 'user'...");
            ProcessBuilder deleteNamespace = new ProcessBuilder("kubectl", "delete", "namespace", "user");
            Process process = deleteNamespace.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                boolean namespaceNotFound = false;

                while ((line = reader.readLine()) != null) {
                    logger.warning("[ERROR] " + line);
                    if (line.contains("not found")) {
                        namespaceNotFound = true;
                    }
                }

                process.waitFor();

                if (namespaceNotFound) {
                    logger.info("Namespace 'user' does not exist.");
                } else {
                    logger.info("Namespace 'user' deleted successfully.");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to delete namespace 'user'.", e);
        }
    }

    public static void deletePatternNamespace() {
        String[] namespaces = {"pattern", "proxy"};

        for (String namespace : namespaces) {
            try {
                logger.info("Deleting namespace '" + namespace + "'...");
                ProcessBuilder deleteNamespace = new ProcessBuilder("kubectl", "delete", "namespace", namespace);
                Process process = deleteNamespace.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    boolean namespaceNotFound = false;

                    while ((line = reader.readLine()) != null) {
                        logger.warning("[ERROR] " + line);
                        if (line.contains("not found")) {
                            namespaceNotFound = true;
                        }
                    }

                    process.waitFor();

                    if (namespaceNotFound) {
                        logger.info("Namespace '" + namespace + "' does not exist.");
                    } else {
                        logger.info("Namespace '" + namespace + "' deleted successfully.");
                    }
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "An error occurred while trying to delete namespace '" + namespace + "'.", e);
            }
        }
    }

    public static void applyYamlFile(String filePath) {
        try {
            logger.info("Applying configuration from file: " + filePath);
            ProcessBuilder apply = new ProcessBuilder("kubectl", "apply", "-f", filePath, "-n", "user");
            Process process = apply.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info(line);
                }
            }

            process.waitFor();
            logger.info("Configuration applied successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to apply configuration.", e);
        }
    }
}
