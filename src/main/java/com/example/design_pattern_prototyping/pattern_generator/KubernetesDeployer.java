package com.example.design_pattern_prototyping.pattern_generator;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class KubernetesDeployer {

    public static boolean startMinikube() {
        try {
            ProcessBuilder builder = new ProcessBuilder("minikube", "status");
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            boolean isRunning = reader.lines().anyMatch(line -> line.contains("Running"));
            process.waitFor();

            // Already running is also a successful state for deployment
            if (!isRunning) {
                System.out.println("Starting Minikube...");
                ProcessBuilder startBuilder = new ProcessBuilder("minikube", "start");
                Process startProcess = startBuilder.start();
                startProcess.waitFor();
                System.out.println("Minikube started successfully.");
            } else {
                System.out.println("Minikube is already running.");
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Failed to start Minikube.");
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
                    namespaceExists = true; // Namespace "user" is already created
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

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            process.waitFor();

            System.out.println("Namespace 'user' deleted successfully.");
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

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                process.waitFor();

                if (process.exitValue() == 0) {
                    System.out.println("Namespace '" + namespace + "' deleted successfully.");
                } else {
                    System.out.println("Failed to delete namespace '" + namespace + "'. Please check logs.");
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
