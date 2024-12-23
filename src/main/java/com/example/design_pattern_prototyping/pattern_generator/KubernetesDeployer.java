package com.example.design_pattern_prototyping.pattern_generator;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KubernetesDeployer {

    public static boolean statusMinikube() {
        try {
            System.out.println("Checking Minikube status...");
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
                System.err.println("Error: Minikube status command exited with code " + exitCode);
                return false;
            }

            // Check if the output contains "Running"
            boolean isRunning = output.contains("Running");
            System.out.println("Minikube status:\n" + output);
            return isRunning;
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to check Minikube status.");
            return false;
        }
    }

    public static boolean startMinikube() {
        try{
            if (statusMinikube()) {
                System.out.println("Minikube is already running.");
                return true; // Minikube is already running
                }
            System.out.println("Starting Minikube...");
            ProcessBuilder startBuilder = new ProcessBuilder("minikube", "start");
            Process startProcess = startBuilder.start();

            // Capture output and error streams
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            // Info messages
            Thread outputThread = new Thread(() -> {
                try (BufferedReader outputReader = new BufferedReader(new InputStreamReader(startProcess.getInputStream()))) {
                    String line;
                    while ((line = outputReader.readLine()) != null) {
                        output.append(line).append(System.lineSeparator());
                        System.out.println("[START OUTPUT] " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            // Error messages
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(startProcess.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        error.append(line).append(System.lineSeparator());
                        System.err.println("[START ERROR] " + line);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            outputThread.start();
            errorThread.start();

            startProcess.waitFor();
            outputThread.join();
            errorThread.join();

            // Check the exit value of the process
            int exitValue = startProcess.exitValue();
            if (exitValue != 0) {
                System.err.println("Critical error starting Minikube. Exit Code: " + exitValue);
                System.err.println("Error Details: " + error.toString());
                return false;
            }

            if (error.length() > 0) {
                System.err.println("Warning during Minikube startup: " + error.toString());
            }

            System.out.println("Minikube started successfully.");
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to start Minikube.");
            return false;
        }
    }

    // Method to stop Minikube
    public static boolean stopMinikube() {
        try {
            System.out.println("Stopping Minikube...");
            ProcessBuilder stopBuilder = new ProcessBuilder("minikube", "stop");
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor();

            System.out.println("Minikube stopped successfully.");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to stop Minikube.");
            return false;
        }
    }
    // Method to create the "user" namespace
    public static void createNamespace() {
        try {
            ProcessBuilder namespace = new ProcessBuilder("kubectl", "create", "namespace", "user");
            Process process = namespace.start();

            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            BufferedReader stdOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            boolean namespaceExists = false;
            while ((line = stdError.readLine()) != null) {
                System.err.println("ERROR: " + line);
                if (line.contains("already exists")) {
                    namespaceExists = true;
                }
            }
            while ((line = stdOutput.readLine()) != null) {
                System.out.println("OUTPUT: " + line);
            }
            process.waitFor();

            if (!namespaceExists) {
                System.out.println("Namespace 'user' created successfully.");
            } else {
                System.out.println("Namespace 'user' already exists.");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to create namespace.");
        }
    }

    // Method to delete the "user" namespace
    public static void deleteUserNamespace() {
        try {
            System.out.println("Deleting namespace 'user'...");
            ProcessBuilder deleteNamespace = new ProcessBuilder("kubectl", "delete", "namespace", "user");
            Process process = deleteNamespace.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            boolean namespaceNotFound = false;

            while ((line = reader.readLine()) != null) {
                System.err.println("[ERROR] " + line);
                if (line.contains("not found")) {
                    namespaceNotFound = true;
                }
            }

            process.waitFor();

            if (namespaceNotFound) {
                System.out.println("Namespace 'user' does not exist.");
            } else {
                System.out.println("Namespace 'user' deleted successfully.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to delete namespace 'user'.");
        }
    }

    // Method to delete the "pattern" and "proxy" namespaces
    public static void deletePatternNamespace() {
        String[] namespaces = {"pattern", "proxy"};

        for (String namespace : namespaces) {
            try {
                System.out.println("Deleting namespace '" + namespace + "'...");
                ProcessBuilder deleteNamespace = new ProcessBuilder("kubectl", "delete", "namespace", namespace);
                Process process = deleteNamespace.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String line;
                boolean namespaceNotFound = false;

                while ((line = reader.readLine()) != null) {
                    System.err.println("[ERROR] " + line);
                    if (line.contains("not found")) {
                        namespaceNotFound = true;
                    }
                }

                process.waitFor();

                if (namespaceNotFound) {
                    System.out.println("Namespace '" + namespace + "' does not exist.");
                } else {
                    System.out.println("Namespace '" + namespace + "' deleted successfully.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("An error occurred while trying to delete namespace '" + namespace + "'.");
            }
        }
    }

    public static void applyYamlFile(String filePath) {
        try {
            ProcessBuilder apply = new ProcessBuilder("kubectl", "apply", "-f", filePath, "-n", "user");
            Process process = apply.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            process.waitFor();

            System.out.println("Configuration applied successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to apply configuration.");
        }
    }
}
