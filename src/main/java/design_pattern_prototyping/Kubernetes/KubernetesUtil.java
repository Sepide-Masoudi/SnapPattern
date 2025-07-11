package design_pattern_prototyping.Kubernetes;

import design_pattern_prototyping.util.UILogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesUtil {

    private static String kubeContext = null;

    private static final Logger logger = Logger.getLogger(KubernetesUtil.class.getName());
    private final UILogger uiLogger;

    public KubernetesUtil(UILogger uiLogger) {
        this.uiLogger = uiLogger;
    }

    public static List<String> getAvailableContexts() throws IOException, InterruptedException {
        List<String> contexts = new ArrayList<>();
        ProcessBuilder pb = new ProcessBuilder("kubectl", "config", "get-contexts", "-o=name");
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    contexts.add(line.trim());
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("kubectl get-contexts failed with code " + exitCode);
        }

        return contexts;
    }

    public static void setKubeContext(String context) {
        kubeContext = context;
    }

    public static String getKubeContext() {
        return kubeContext;
    }

    private static void injectContext(List<String> command) {
        String context = getKubeContext();
        if (context != null && !context.isBlank()) {
            command.add(1, context);
            command.add(1, "--context");
        }
    }

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
            ProcessBuilder startBuilder = new ProcessBuilder("minikube", "start", "--driver=docker");
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

    public static boolean deleteMinikube() {
        try {
            logger.info("Stopping Minikube...");
            ProcessBuilder stopBuilder = new ProcessBuilder("minikube", "delete");
            Process stopProcess = stopBuilder.start();
            stopProcess.waitFor();

            logger.info("Minikube deleted successfully.");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to delete Minikube.", e);
            return false;
        }
    }

    public static void createNamespace(String namespaceName) {
        try {
            logger.info("Creating namespace '" + namespaceName + "'...");
            List<String> command = new ArrayList<>(List.of("kubectl", "create", "namespace", namespaceName));
            injectContext(command);

            ProcessBuilder namespace = new ProcessBuilder(command);
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
                    logger.info("Namespace '" + namespaceName + "' created successfully.");
                } else {
                    logger.info("Namespace '" + namespaceName + "' already exists.");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create namespace '" + namespaceName + "'.", e);
        }
    }

    public static void deleteUserNamespace() {
        try {
            logger.info("Deleting namespace 'user'...");
            List<String> command = new ArrayList<>(List.of("kubectl", "delete", "namespace", "user"));
            injectContext(command);

            ProcessBuilder deleteNamespace = new ProcessBuilder(command);
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

    public static void deletePattern() {
        String[] namespaces = {"pattern", "proxy"};

        try {
            logger.info("Deleting all resources with label app=pattern...");
            List<String> deleteLabeledResources = new ArrayList<>(List.of(
                    "kubectl", "delete", "all,svc,cm,secret,deploy,pod", "-l", "app=pattern", "--all-namespaces"
            ));
            injectContext(deleteLabeledResources);

            ProcessBuilder deleteResourcesProcess = new ProcessBuilder(deleteLabeledResources);
            Process process = deleteResourcesProcess.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[INFO] " + line);
                }
            }

            process.waitFor();
            logger.info("Labeled resources deleted.");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error deleting labeled resources.", e);
        }

        for (String namespace : namespaces) {
            try {
                logger.info("Deleting namespace '" + namespace + "'...");
                List<String> command = new ArrayList<>(List.of("kubectl", "delete", "namespace", namespace));
                injectContext(command);

                ProcessBuilder deleteNamespace = new ProcessBuilder(command);
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

    public static void applyYaml(String filePath) throws IOException, InterruptedException {
        applyYaml(filePath, null);
    }

    public static void applyYaml(String filePath, String namespace) throws IOException, InterruptedException {
        logger.info("Applying configuration from file: " + filePath);

        List<String> command = new ArrayList<>(List.of("kubectl", "apply", "-f", filePath));
        if (namespace != null && !namespace.isBlank()) {
            command.add("-n");
            command.add(namespace);
        }
        injectContext(command);

        ProcessBuilder apply = new ProcessBuilder(command);
        apply.redirectErrorStream(true);
        Process process = apply.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.lines().forEach(line -> logger.info("[kubectl] " + line));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("kubectl apply failed (exit code " + exitCode + ") for file: " + filePath);
        }

        logger.info("Configuration applied successfully.");
    }

    public static void executeCommand(String... command) throws IOException, InterruptedException {
        List<String> commandList = new ArrayList<>(Arrays.asList(command));
        KubernetesUtil.injectContext(commandList);

        ProcessBuilder processBuilder = new ProcessBuilder(commandList);
        Process process = processBuilder.start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("[stdout] " + line);
                    //uiLogger.info("[stdout] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stdout of process", e);
                //uiLogger.warning("Error reading stdout of process: " + e.getMessage());
            }
        }).start();

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.warning("[stderr] " + line);
                    //uiLogger.warning("[stderr] " + line);
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error reading stderr of process", e);
                //uiLogger.warning("Error reading stderr of process: " + e.getMessage());
            }
        }).start();

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }

    public static void getServiceYamlToFile(String serviceName, String namespace, Path targetFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("kubectl", "get", "svc", serviceName, "-n", namespace, "-o", "yaml"));
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to get service YAML for " + serviceName + ", exit code: " + exitCode);
        }

        Files.writeString(targetFile, output.toString());
    }

    public static void getDeploymentYamlToFile(String deployName, String namespace, Path targetFile) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("kubectl", "get", "deployment", deployName, "-n", namespace, "-o", "yaml"));
        ProcessBuilder builder = new ProcessBuilder(command);
        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Failed to get deployment YAML for " + deployName + ", exit code: " + exitCode);
        }

        Files.writeString(targetFile, output.toString());
    }
}
