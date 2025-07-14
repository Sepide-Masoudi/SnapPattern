package design_pattern_prototyping.controller;

import com.google.gson.Gson;
import design_pattern_prototyping.Kubernetes.KubernetesUtil;
import design_pattern_prototyping.Kubernetes.KubernetesClientAPI;
import design_pattern_prototyping.pattern_generator.*;
import design_pattern_prototyping.pattern_generator.AsyncRequestReplyGenerator;
import design_pattern_prototyping.pattern_generator.CircuitBreakerGenerator;
import design_pattern_prototyping.pattern_generator.PatternGenerator;
import design_pattern_prototyping.pattern_generator.PatternGeneratorFactory;
import design_pattern_prototyping.util.AsyncRequestReply.AsyncPatternConfig;
import design_pattern_prototyping.util.CacheAside.CacheAsidePatternConfig;
import design_pattern_prototyping.util.CacheAsideSQL.CacheAsideSQLDialogs;
import design_pattern_prototyping.util.CacheAsideSQL.ProxySQLRule;
import design_pattern_prototyping.util.CacheAsideSQL.ProxySQLUser;
import design_pattern_prototyping.util.CircuitBreaker.CBPatternConfig;
import design_pattern_prototyping.util.CircuitBreaker.ConfigDialogUtil;
import design_pattern_prototyping.util.UILogger;
import design_pattern_prototyping.util.YamlEditor;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.File;
import java.util.stream.Collectors;

public class PatternController {

    private static final Logger logger = Logger.getLogger(PatternController.class.getName());
    private UILogger uiLogger;
    private KubernetesClientAPI kubeClientAPI;

    @FXML private TextArea logTextArea;
    @FXML private Label statusLabel;
    @FXML private VBox patternFieldsBox;
    @FXML private Button buildPatternButton;
    @FXML private ComboBox<String> patternDropdown;
    @FXML private VBox asyncRequestReplyFields;
    @FXML private VBox gatewayOffloadingFields;
    @FXML private VBox requestCollapsingFields;
    @FXML private VBox cacheAsideFields;
    @FXML private VBox cacheAsideSQLFields;
    @FXML private  VBox circuitBreakerFields;

    // Asynch Request Reply Pattern
    @FXML private TableView<AsyncPatternConfig> asyncTable;
    @FXML private TableColumn<AsyncPatternConfig, String> backendNameColumn;
    @FXML private TableColumn<AsyncPatternConfig, String> backendPortColumn;
    @FXML private TableColumn<AsyncPatternConfig, String> endpointPathColumn;

    private ObservableList<AsyncPatternConfig> asyncServiceList = FXCollections.observableArrayList();
    private boolean asyncTableInitialized = false;

    // Gateway Offloading
    @FXML private TextField go_serviceEndpoint;
    @FXML private TextField go_serviceName;
    @FXML private TextField go_servicePort;

    // Cache Aside Pattern
    @FXML private TableView<CacheAsidePatternConfig> cacheAsideTable;
    @FXML private TableColumn<CacheAsidePatternConfig, String> ca_backendService;
    @FXML private TableColumn<CacheAsidePatternConfig, String> ca_backendPort;
    @FXML private TableColumn<CacheAsidePatternConfig, String> ca_cachedEndpoints;
    @FXML private TableColumn<CacheAsidePatternConfig, String> ca_cacheTTL;
    @FXML private TableColumn<CacheAsidePatternConfig, String> ca_maxConnections;
    @FXML private TextField ca_redisReplicas;
    @FXML private TextField ca_redisNodes;

    private ObservableList<CacheAsidePatternConfig> cacheAsideServiceList = FXCollections.observableArrayList();
    private boolean cacheAsideTableInitialized = false;

    // Cache Aside MySQL Pattern
    @FXML private TextField sqlDbServiceName;
    @FXML private TextField sqlDbServicePort;
    @FXML private TextField sqlProxyThreads;
    @FXML private TextField sqlProxyMaxConnections;
    @FXML private TextField sqlProxyMonitorUser;
    @FXML private TextField sqlProxyMonitorPass;
    @FXML private TextField sqlQueryCacheSize;

    @FXML private TableView<ProxySQLUser> proxySQLUsersTable;
    @FXML private TableColumn<ProxySQLUser, String> proxySQL_user;
    @FXML private TableColumn<ProxySQLUser, String> proxySQL_password;

    @FXML private TableView<ProxySQLRule> proxySQLRulesTable;
    @FXML private TableColumn<ProxySQLRule, String> proxySQL_QueryRegex;
    @FXML private TableColumn<ProxySQLRule, String> proxySQL_ttl;

    private boolean proxySQLUsersTableInitialized = false;
    private boolean proxySQLRulesTableInitialized = false;

    // Request Collapsing Pattern
    @FXML private TextField rc_backendService;
    @FXML private TextField rc_backendPort;
    @FXML private TextField rc_endpointPath;
    @FXML private TextField rc_queryParam;
    @FXML private TextField rc_idField;
    @FXML private TextField rc_batchQuery;

    @FXML private TextField rc_dbHost;
    @FXML private TextField rc_dbPort;
    @FXML private TextField rc_dbName;
    @FXML private TextField rc_dbUser;
    @FXML private TextField rc_dbPass;

    //Circuit Breaker Pattern
    @FXML private TableView<CBPatternConfig> circuitBreakerTable;
    @FXML private TableColumn<CBPatternConfig, String> cbColService;
    @FXML private TableColumn<CBPatternConfig, String> cbColRoute;
    @FXML private TableColumn<CBPatternConfig, Integer> cbColPort;
    @FXML private TableColumn<CBPatternConfig, Integer> cbColMaxConn;
    @FXML private TableColumn<CBPatternConfig, Integer> cbColMaxPend;
    @FXML private TableColumn<CBPatternConfig, Integer> cbColMaxReq;
    @FXML private TableColumn<CBPatternConfig, Integer> cbColRetry;
    @FXML private TableColumn<CBPatternConfig, String> cbColTimeout;

    private final ObservableList<CBPatternConfig> circuitBreakerServiceList = FXCollections.observableArrayList();
    private boolean circuitBreakerTableInitialized = false;


    private File yamlFile;
    private File instrumentedYamlFile;

    @FXML
    public void initialize() {
        ControllerMediatorImpl.getInstance().registerPatternBuilderController(this);
        patternDropdown.setValue("Baseline");

        Logger logger = Logger.getLogger("PatternLogger");
        uiLogger = new UILogger(logTextArea, logger);
        logger.info("PatternBuilderController initialized.");
    }

    private void initializeAsyncTable() {
        if (asyncTableInitialized) return;

        backendNameColumn.setCellValueFactory(cellData -> cellData.getValue().serviceNameProperty());
        backendPortColumn.setCellValueFactory(cellData -> cellData.getValue().servicePortProperty());
        endpointPathColumn.setCellValueFactory(cellData -> cellData.getValue().endpointPathProperty());
        asyncTable.setItems(asyncServiceList);

        asyncTableInitialized = true;
    }

    private void initializeCircuitBreakerTable() {
        if (circuitBreakerTableInitialized) return;

        cbColService.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getServiceName()));
        cbColRoute.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRoutePrefix()));
        cbColPort.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getPort()).asObject());
        cbColMaxConn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getMaxConnections()).asObject());
        cbColMaxPend.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getMaxPendingRequests()).asObject());
        cbColMaxReq.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getMaxRequests()).asObject());
        cbColRetry.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getRetryAttempts()).asObject());
        cbColTimeout.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPerTryTimeout()));

        circuitBreakerTable.setItems(circuitBreakerServiceList);
        circuitBreakerTableInitialized = true;
    }

    private void initializecacheAsideTable() {
        if (cacheAsideTableInitialized) return;

        ca_backendService.setCellValueFactory(cellData -> cellData.getValue().backendServiceProperty());
        ca_backendPort.setCellValueFactory(cellData -> cellData.getValue().backendPortProperty());
        ca_cachedEndpoints.setCellValueFactory(cellData -> cellData.getValue().cachedEndpointsProperty());
        ca_cacheTTL.setCellValueFactory(cellData -> cellData.getValue().cacheTTLProperty());
        ca_maxConnections.setCellValueFactory(cellData -> cellData.getValue().maxConnectionsProperty());
        cacheAsideTable.setItems(cacheAsideServiceList);

        cacheAsideTableInitialized = true;
    }

    @FXML
    private void initializecacheAsideSQLTable() {
        if (!proxySQLUsersTableInitialized) {
            proxySQL_user.setCellValueFactory(cellData -> cellData.getValue().usernameProperty());
            proxySQL_password.setCellValueFactory(cellData -> cellData.getValue().passwordProperty());
            proxySQLUsersTable.setItems(FXCollections.observableArrayList());

            proxySQLUsersTableInitialized = true;
        }

        if (!proxySQLRulesTableInitialized) {
            proxySQL_QueryRegex.setCellValueFactory(cellData -> cellData.getValue().regexPatternProperty());
            proxySQL_ttl.setCellValueFactory(cellData -> cellData.getValue().ttlProperty());
            proxySQLRulesTable.setItems(FXCollections.observableArrayList());

            proxySQLRulesTableInitialized = true;
        }
    }

    @FXML
    public void handleFileUpload() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML Files", "*.yaml", "*.yml"));
        yamlFile = fileChooser.showOpenDialog(new Stage());
        if (yamlFile != null) {
            logger.info("YAML file selected: " + yamlFile.getAbsolutePath());
            uiLogger.info("YAML file selected: " + yamlFile.getAbsolutePath());
            statusLabel.setText("YAML file selected: " + yamlFile.getName());
        } else {
            statusLabel.setText("No YAML file selected.");
            uiLogger.warning("No YAML file selected.");
        }
    }

    @FXML
    public void openInstrumentationModal() {
        if (yamlFile == null) {
            showAlert(Alert.AlertType.WARNING, "Missing YAML", "Please upload a YAML configuration file first.");
            return;
        }

        try {
            String content = Files.readString(yamlFile.toPath());
            List<String> deploymentNames = YamlEditor.extractDeploymentNames(content);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/design_pattern_prototyping/DeploymentController.fxml"));
            Parent root = loader.load();
            DeploymentController controller = loader.getController();
            controller.setDeployments(deploymentNames);
            controller.setOriginalYamlFile(yamlFile);

            Stage modal = new Stage();
            modal.setTitle("Set Instrumentation Language");
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setScene(new Scene(root));
            modal.showAndWait();

            File instrumented = controller.getInstrumentedamlFile();
            if (instrumented != null) {
                this.instrumentedYamlFile = instrumented;
                Map<String, String> languageMap = controller.getConfirmedLanguageMap();
            }

        } catch (Exception e) {
            String errorMsg = "Failed to process YAML file. Please check its structure and indentation.\n" +
                    "Error: " + e.getMessage();

            logger.log(Level.SEVERE, "YAML parsing failed", e);
            uiLogger.error(errorMsg);
            showAlert(Alert.AlertType.ERROR, "YAML Error", errorMsg);
        }
    }

    @FXML
    public void deleteApplication() {
        logger.info("User requested to delete the user application...");
        uiLogger.info("User requested to delete the user application...");
        statusLabel.setText("Deleting user application...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesUtil.startMinikube();
                if (minikubeStarted) {
                    KubernetesUtil.deleteUserNamespace();
                    KubernetesUtil.deletePattern();
                    logger.info("User application services deleted successfully...");
                    uiLogger.info("User application services deleted successfully...");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("user application deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    uiLogger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during user application deletion", e);
                uiLogger.error("Error during user application deletion: " + e.getMessage());
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during user application deletion."));
            }
        }).start();
    }

    @FXML
    public void deletePattern() {
        logger.info("User requested to delete the pattern...");
        uiLogger.info("User requested to delete the pattern...");
        statusLabel.setText("Deleting current pattern...");

        new Thread(() -> {
            try {
                boolean minikubeStarted = KubernetesUtil.startMinikube();
                if (minikubeStarted) {
                    KubernetesUtil.deletePattern();
                    uiLogger.info("Pattern deleted successfully.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Pattern deleted successfully."));
                } else {
                    logger.warning("Failed to start Minikube. Deletion aborted.");
                    uiLogger.warning("Failed to start Minikube. Deletion aborted.");
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deletion aborted."));
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error during pattern deletion", e);
                uiLogger.error("Error during pattern deletion: " + e.getMessage());
                javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during pattern deletion."));
            }
        }).start();
    }

    @FXML
    public void deployApplication() {
        File fileToDeploy = (instrumentedYamlFile != null) ? instrumentedYamlFile : yamlFile;

        if (fileToDeploy != null) {
            logger.info("User requested to deploy the application with configuration: " + fileToDeploy.getAbsolutePath());
            uiLogger.info("Deploying application with configuration: " + fileToDeploy.getAbsolutePath());
            statusLabel.setText("Deploying application configuration...");

            new Thread(() -> {
                try {
                    boolean minikubeStarted = KubernetesUtil.startMinikube();
                    if (!minikubeStarted) {
                        logger.warning("Failed to start Minikube. Deployment aborted.");
                        uiLogger.warning("Failed to start Minikube. Deployment aborted.");
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Failed to start Kubernetes. Deployment aborted."));
                        return;
                    }

                    KubernetesUtil.createNamespace("user");

                    try {
                        KubernetesUtil.applyYaml(fileToDeploy.getAbsolutePath(), "user");
                        uiLogger.info("Application configuration applied.");
                        logger.info("YAML applied successfully: " + fileToDeploy.getAbsolutePath());
                        javafx.application.Platform.runLater(() -> statusLabel.setText("Configuration applied successfully."));
                    } catch (IOException | InterruptedException applyEx) {
                        logger.log(Level.SEVERE, "Failed to apply YAML configuration", applyEx);
                        uiLogger.error("Failed to apply application configuration: " + applyEx.getMessage());
                        javafx.application.Platform.runLater(() -> statusLabel.setText("YAML application failed."));
                    }

                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during application deployment", e);
                    uiLogger.error("Error during application deployment: " + e.getMessage());
                    javafx.application.Platform.runLater(() -> statusLabel.setText("Error occurred during application deployment."));
                }
            }).start();

        } else {
            logger.warning("No YAML file selected. Deployment aborted.");
            uiLogger.warning("No YAML file selected. Deployment aborted.");
            showAlert(Alert.AlertType.WARNING, "Deploy Application", "Please select a YAML configuration file first.");
        }
    }

    @FXML
    public void handlePatternSelection() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User selected pattern: " + selectedPattern);
        uiLogger.info("User selected pattern: " + selectedPattern);

        buildPatternButton.setDisable("Baseline".equals(selectedPattern));

        patternFieldsBox.setVisible(!"Baseline".equals(selectedPattern));

        asyncRequestReplyFields.setVisible(false);
        gatewayOffloadingFields.setVisible(false);
        requestCollapsingFields.setVisible(false);
        cacheAsideFields.setVisible(false);
        cacheAsideSQLFields.setVisible(false);
        circuitBreakerFields.setVisible(false);

        switch (selectedPattern) {
            case "Async Request Reply":
                asyncRequestReplyFields.setVisible(true);
                initializeAsyncTable();
                break;
            case "Gateway Offloading":
                gatewayOffloadingFields.setVisible(true);
                break;
            case "Request Collapsing":
                requestCollapsingFields.setVisible(true);
                break;
            case "Cache Aside":
                cacheAsideFields.setVisible(true);
                initializecacheAsideTable();
                break;
            case "Cache Aside (MySQL Proxy)":
                cacheAsideSQLFields.setVisible(true);
                initializecacheAsideSQLTable();
                break;
            case "Circuit Breaker":
                circuitBreakerFields.setVisible(true);
                initializeCircuitBreakerTable();
                break;
            default:
                break;
        }
    }

    @FXML
    public void buildPattern() {
        String selectedPattern = patternDropdown.getValue();
        logger.info("User requested to build pattern: " + selectedPattern);
        uiLogger.info("User requested to build pattern: " + selectedPattern);
        statusLabel.setText("Deploying pattern: " + selectedPattern);

        // Execute everything in background
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    PatternGenerator generator = PatternGeneratorFactory.getGenerator(selectedPattern);
                    KubernetesUtil.createNamespace("pattern");

                    if ("Async Request Reply".equals(selectedPattern)) {
                        if (generator instanceof AsyncRequestReplyGenerator asyncGen) {
                            if (asyncServiceList.isEmpty()) {
                                showWarning("Missing Input", "Please add at least one async service configuration.");
                                return null;
                            }

                            List<Map<String, String>> configList = asyncServiceList.stream()
                                    .map(config -> Map.of(
                                            "BACKEND_NAME", config.getServiceName(),
                                            "BACKEND_PORT", config.getServicePort(),
                                            "ENDPOINT_PATH", config.getEndpointPath()
                                    )).toList();

                            asyncGen.generatePattern(configList);
                            asyncGen.deployPattern();
                        }
                    }
                    else if ("Circuit Breaker".equals(selectedPattern)) {
                        if (generator instanceof CircuitBreakerGenerator cbGen) {
                            if (circuitBreakerServiceList.isEmpty()) {
                                showWarning("Missing Input", "Please add at least one circuit breaker configuration.");
                                return null;
                            }

                            List<Map<String, String>> configList = circuitBreakerServiceList.stream()
                                    .map(config -> Map.of(
                                            "SERVICE_NAME", config.getServiceName(),
                                            "ROUTE_PREFIX", config.getRoutePrefix(),
                                            "PORT", String.valueOf(config.getPort()),
                                            "MAX_CONNECTIONS", String.valueOf(config.getMaxConnections()),
                                            "MAX_PENDING_REQUESTS", String.valueOf(config.getMaxPendingRequests()),
                                            "MAX_REQUESTS", String.valueOf(config.getMaxRequests()),
                                            "RETRY_ATTEMPTS", String.valueOf(config.getRetryAttempts()),
                                            "PER_TRY_TIMEOUT", config.getPerTryTimeout()
                                    )).toList();

                            cbGen.generatePattern(configList);
                            cbGen.deployPattern();
                        }
                    }
                    else if ("Cache Aside".equals(selectedPattern)) {
                        if (generator instanceof CacheAsideGenerator caGen) {
                            if (cacheAsideServiceList.isEmpty()) {
                                showWarning("Missing Input", "Please add at least one cache-aside service configuration.");
                                return null;
                            }
                            if (ca_redisReplicas.getText().isBlank() || ca_redisNodes.getText().isBlank()) {
                                showWarning("Missing Input", "Please specify both Redis Replicas and Redis Nodes.");
                                return null;
                            }

                            List<Map<String, String>> configList = cacheAsideServiceList.stream()
                                    .map(config -> Map.of(
                                            "BACKEND_SERVICE", config.getBackendService(),
                                            "BACKEND_PORT", config.getBackendPort(),
                                            "CACHED_ENDPOINTS", config.getCachedEndpoints(),
                                            "CACHE_TTL", config.getCacheTTL(),
                                            "MAX_CONNECTIONS", config.getMaxConnections()
                                    )).collect(Collectors.toList());

                            if (!configList.isEmpty()) {
                                Map<String, String> enriched = new HashMap<>(configList.get(0));
                                enriched.put("REDIS_REPLICAS", ca_redisReplicas.getText());
                                enriched.put("REDIS_NODES", ca_redisNodes.getText());
                                configList.set(0, enriched);
                            }

                            caGen.generatePattern(configList);
                            caGen.deployPattern();
                        }
                    }
                    else if ("Cache Aside (MySQL Proxy)".equals(selectedPattern)) {
                        if (generator instanceof CacheAsideSQLGenerator caSQLGen) {
                            if (sqlDbServiceName.getText().isBlank() ||
                                    sqlDbServicePort.getText().isBlank() ||
                                    sqlProxyThreads.getText().isBlank() ||
                                    sqlProxyMaxConnections.getText().isBlank() ||
                                    sqlProxyMonitorUser.getText().isBlank() ||
                                    sqlProxyMonitorPass.getText().isBlank() ||
                                    sqlQueryCacheSize.getText().isBlank()) {
                                showWarning("Missing Input", "Please fill in all ProxySQL configuration fields.");
                                return null;
                            }

                            if (proxySQLUsersTable.getItems().isEmpty()) {
                                showWarning("Missing Input", "Please add at least one MySQL user.");
                                return null;
                            }

                            if (proxySQLRulesTable.getItems().isEmpty()) {
                                showWarning("Missing Input", "Please add at least one Query Rule.");
                                return null;
                            }

                            Map<String, String> configMap = Map.of(
                                    "DB_SERVICE_NAME", sqlDbServiceName.getText().trim(),
                                    "DB_SERVICE_PORT", sqlDbServicePort.getText().trim(),
                                    "PROXY_THREADS", sqlProxyThreads.getText().trim(),
                                    "PROXY_MAX_CONNECTIONS", sqlProxyMaxConnections.getText().trim(),
                                    "MONITOR_USER", sqlProxyMonitorUser.getText().trim(),
                                    "MONITOR_PASSWORD", sqlProxyMonitorPass.getText().trim(),
                                    "QUERY_CACHE_SIZE", sqlQueryCacheSize.getText().trim()
                            );

                            List<Map<String, String>> userList = proxySQLUsersTable.getItems().stream()
                                    .map(user -> Map.of(
                                            "username", user.getUsername(),
                                            "password", user.getPassword()
                                    )).toList();

                            List<Map<String, String>> ruleList = proxySQLRulesTable.getItems().stream()
                                    .map(rule -> Map.of(
                                            "pattern", rule.getRegexPattern(),
                                            "ttl", rule.getTtl()
                                    )).toList();

                            Map<String, String> enrichedConfig = new HashMap<>(configMap);
                            enrichedConfig.put("USERS_JSON", new Gson().toJson(userList));
                            enrichedConfig.put("RULES_JSON", new Gson().toJson(ruleList));

                            caSQLGen.generatePattern(List.of(enrichedConfig));
                            caSQLGen.deployPattern();
                        }
                    }
                    else {
                        Map<String, String> parameters = getStringStringMap(selectedPattern);
                        generator.generatePattern(parameters);
                        generator.deployPattern();
                    }

                    logger.info("Pattern " + selectedPattern + " deployed successfully.");
                    uiLogger.info("Pattern " + selectedPattern + " deployed successfully.");

                    Platform.runLater(() -> statusLabel.setText("Pattern " + selectedPattern + " deployed successfully."));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error during pattern generation or deployment", e);
                    uiLogger.error("Error during pattern generation or deployment: " + e.getMessage());
                    Platform.runLater(() -> {
                        statusLabel.setText("Error occurred while building the pattern.");
                        showAlert(Alert.AlertType.ERROR, "Build Pattern", "An error occurred while building the pattern: " + e.getMessage());
                    });
                }
                return null;
            }
        };

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    @NotNull
    private Map<String, String> getStringStringMap(String selectedPattern) {
        Map<String, String> parameters = new HashMap<>();

        if ("Gateway Offloading".equals(selectedPattern)) {
            parameters.put("SERVICE_HOST", go_servicePort.getText());
            parameters.put("SERVICE_ENDPOINT", go_serviceEndpoint.getText());
            parameters.put("SERVICE_NAME", go_serviceName.getText());
        } else if ("Request Collapsing".equals(selectedPattern)) {
            parameters.put("SERVICE_NAME", rc_backendService.getText());  // Needed by generator
            parameters.put("SERVICE_PORT", rc_backendPort.getText());
            parameters.put("ENDPOINT_PATH", rc_endpointPath.getText());
            parameters.put("COLLAPSER_PATH", rc_endpointPath.getText());
            parameters.put("QUERY_PARAM", rc_queryParam.getText());
            parameters.put("ID_FIELD", rc_idField.getText());
            parameters.put("DB_HOST", rc_dbHost.getText());
            parameters.put("DB_PORT", rc_dbPort.getText());
            parameters.put("DB_NAME", rc_dbName.getText());
            parameters.put("DB_USER", rc_dbUser.getText());
            parameters.put("DB_PASS", rc_dbPass.getText());
            parameters.put("BATCH_QUERY", rc_batchQuery.getText());
        }
        return parameters;
    }

    public String getSelectedPattern(){
        return patternDropdown.getValue();
    }

    @FXML
    public void handleAsyncAddRow() {
        design_pattern_prototyping.util.AsyncRequestReply.ConfigDialogUtil.showAddDialog().ifPresent(asyncServiceList::add);
    }

    @FXML
    public void handleAsyncEditRow() {
        AsyncPatternConfig selected = asyncTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            design_pattern_prototyping.util.AsyncRequestReply.ConfigDialogUtil.showEditDialog(selected).ifPresent(cfg -> asyncTable.refresh());
        }
    }

    @FXML
    public void handleAsyncDeleteRow() {
        AsyncPatternConfig selected = asyncTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            asyncServiceList.remove(selected);
        }
    }

    @FXML
    public void handleCircuitBreakerAddRow() {
        ConfigDialogUtil.showAddDialog().ifPresent(circuitBreakerServiceList::add);
    }

    @FXML
    public void handleCircuitBreakerEditRow() {
        CBPatternConfig selected = circuitBreakerTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            ConfigDialogUtil.showEditDialog(selected).ifPresent(config -> circuitBreakerTable.refresh());
        }
    }

    @FXML
    public void handleCircuitBreakerDeleteRow() {
        CBPatternConfig selected = circuitBreakerTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            circuitBreakerServiceList.remove(selected);
        }
    }

    @FXML
    public void handleCacheAsideAddRow() {
        design_pattern_prototyping.util.CacheAside.ConfigDialogUtil.showAddDialog().ifPresent(cacheAsideServiceList::add);
    }

    @FXML
    public void handleCacheAsideEditRow() {
        CacheAsidePatternConfig selected = cacheAsideTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            design_pattern_prototyping.util.CacheAside.ConfigDialogUtil.showEditDialog(selected).ifPresent(cfg -> cacheAsideTable.refresh());
        }
    }

    @FXML
    public void handleCacheAsideDeleteRow() {
        CacheAsidePatternConfig selected = cacheAsideTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            cacheAsideServiceList.remove(selected);
        }
    }

    @FXML
    public void handleAddSQLUser() {
        List<ProxySQLUser> newUsers = CacheAsideSQLDialogs.showDBUserDialog((Stage) patternDropdown.getScene().getWindow());
        ObservableList<ProxySQLUser> currentItems = proxySQLUsersTable.getItems();
        currentItems.addAll(newUsers);
    }

    @FXML
    public void handleAddSQLRule() {
        List<ProxySQLRule> newRules = CacheAsideSQLDialogs.showQueryRuleDialog((Stage) patternDropdown.getScene().getWindow());
        ObservableList<ProxySQLRule> currentItems = proxySQLRulesTable.getItems();
        currentItems.addAll(newRules);
    }

    @FXML
    public void handleEditSQLUser() {
        ProxySQLUser selectedUser = proxySQLUsersTable.getSelectionModel().getSelectedItem();
        if (selectedUser != null) {
            CacheAsideSQLDialogs.showEditUserDialog(selectedUser).ifPresent(updated -> {
                selectedUser.setUsername(updated.getUsername());
                selectedUser.setPassword(updated.getPassword());
                proxySQLUsersTable.refresh();
            });
        }
    }

    @FXML
    public void handleEditSQLRule() {
        ProxySQLRule selectedRule = proxySQLRulesTable.getSelectionModel().getSelectedItem();
        if (selectedRule != null) {
            CacheAsideSQLDialogs.showEditRuleDialog(selectedRule).ifPresent(updated -> {
                selectedRule.setRegexPattern(updated.getRegexPattern());
                selectedRule.setTtl(updated.getTtl());
                proxySQLRulesTable.refresh();
            });
        }
    }

    @FXML
    public void handleDeleteSQLUser() {
        ProxySQLUser selected = proxySQLUsersTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            proxySQLUsersTable.getItems().remove(selected);
        }
    }

    @FXML
    public void handleDeleteSQLRule() {
        ProxySQLRule selected = proxySQLRulesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            proxySQLRulesTable.getItems().remove(selected);
        }
    }

    private void showWarning(String title, String message) {
        Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, title, message));
    }

    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
