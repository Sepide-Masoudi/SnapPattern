package design_pattern_prototyping.pattern_generator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import design_pattern_prototyping.Kubernetes.KubernetesUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CacheAsideSQLGenerator implements PatternGenerator {

    private static final Logger logger = Logger.getLogger(CacheAsideSQLGenerator.class.getName());
    private static final String PROXY_SERVICE = "src/main/resources/Patterns/CacheAside/sqlcache/proxysql-service.yml";
    private static final String PROXY_DEPLOYMENT = "src/main/resources/Patterns/CacheAside/sqlcache/proxysql-deployment.yml";
    private static final String NAMESPACE = "pattern";

    Path tempProxySQLService = null;
    Path tempProxySQLDeployment = null;

    @Override
    public void generatePattern(Map<String, String> parameters) {
        generatePattern(List.of(parameters));
    }

    @Override
    public void generatePattern(List<Map<String, String>> parameters) {
        try {
            Map<String, String> config = parameters.get(0);

            String dbService = config.get("DB_SERVICE_NAME");
            String dbPort = config.get("DB_SERVICE_PORT");
            String threads = config.get("PROXY_THREADS");
            String maxConnections = config.get("PROXY_MAX_CONNECTIONS");
            String monitorUser = config.get("MONITOR_USER");
            String monitorPassword = config.get("MONITOR_PASSWORD");
            String queryCacheSize = config.get("QUERY_CACHE_SIZE");

            List<Map<String, String>> users = new Gson().fromJson(config.get("USERS_JSON"),
                    new TypeToken<List<Map<String, String>>>() {}.getType());
            List<Map<String, String>> rules = new Gson().fromJson(config.get("RULES_JSON"),
                    new TypeToken<List<Map<String, String>>>() {}.getType());

            // Rename original DB service
            renameBackendService(dbService);

            StringBuilder cnf = new StringBuilder();

            cnf.append("datadir=\"/var/lib/proxysql\"\n\n");

            cnf.append("admin_variables=\n{\n")
                    .append("  admin_credentials=\"admin:admin\"\n")
                    .append("  mysql_ifaces=\"0.0.0.0:6032\"\n")
                    .append("  refresh_interval=2000\n")
                    .append("}\n\n");

            cnf.append("mysql_variables=\n{\n")
                    .append("  threads=").append(threads).append("\n")
                    .append("  max_connections=").append(maxConnections).append("\n")
                    .append("  enable_connection_pool=true\n")
                    .append("  default_query_delay=0\n")
                    .append("  default_query_timeout=36000000\n")
                    .append("  have_compress=true\n")
                    .append("  poll_timeout=2000\n")
                    .append("  interfaces=\"0.0.0.0:3306;/tmp/proxysql.sock\"\n")
                    .append("  default_schema=\"information_schema\"\n")
                    .append("  stacksize=1048576\n")
                    .append("  server_version=\"5.1.30\"\n")
                    .append("  connect_timeout_server=10000\n")
                    .append("  monitor_history=60000\n")
                    .append("  monitor_connect_interval=200000\n")
                    .append("  monitor_ping_interval=200000\n")
                    .append("  ping_interval_server_msec=10000\n")
                    .append("  ping_timeout_server=200\n")
                    .append("  commands_stats=true\n")
                    .append("  sessions_sort=true\n")
                    .append("  monitor_username=\"").append(monitorUser).append("\"\n")
                    .append("  monitor_password=\"").append(monitorPassword).append("\"\n")
                    .append("  mysql-query_cache_size_MB=").append(queryCacheSize).append("\n")
                    .append("  mysql-query_cache_strip_comments=true\n")
                    .append("}\n\n");

            cnf.append("mysql_servers=\n(\n")
                    .append("  {\n")
                    .append("    address=\"").append(dbService).append("-backend.user.svc.cluster.local\",\n")
                    .append("    port=").append(dbPort).append(",\n")
                    .append("    hostgroup=0,\n")
                    .append("    max_connections=300,\n")
                    .append("    monitor_user=\"").append(monitorUser).append("\",\n")
                    .append("    monitor_password=\"").append(monitorPassword).append("\"\n")
                    .append("  }\n")
                    .append(")\n\n");

            cnf.append("mysql_users=\n(\n");
            for (int i = 0; i < users.size(); i++) {
                Map<String, String> user = users.get(i);
                cnf.append("  { username = \"").append(user.get("username"))
                        .append("\", password = \"").append(user.get("password"))
                        .append("\", default_hostgroup = 0, active = 1 }");
                if (i < users.size() - 1) cnf.append(",");
                cnf.append("\n");
            }
            cnf.append(")\n\n");

            cnf.append("mysql_query_rules=\n(\n");
            int ruleId = 10;
            for (Map<String, String> rule : rules) {
                cnf.append("  {\n")
                        .append("    rule_id=").append(ruleId).append("\n")
                        .append("    active=1\n")
                        .append("    match_pattern=\"").append(rule.get("pattern")).append("\"\n")
                        .append("    destination_hostgroup=0\n")
                        .append("    apply=1\n");
                if (rule.containsKey("ttl") && !rule.get("ttl").isBlank()) {
                    cnf.append("    cache_ttl=").append(rule.get("ttl")).append("\n");
                }
                cnf.append("  }");
                ruleId += 5;
                if (ruleId < 99) cnf.append(",");
                cnf.append("\n");
            }

            // Default rule - directly forward all other queries without caching
            cnf.append("  {\n")
                    .append("    rule_id=99\n")
                    .append("    active=1\n")
                    .append("    match_pattern=\".*\"\n")
                    .append("    destination_hostgroup=0\n")
                    .append("    apply=1\n")
                    .append("  }\n")
                    .append(")\n");

            // generate proxysql.cnf file
            Path tempDir = Files.createTempDirectory("proxysql-temp-");

            Path cnfFile = tempDir.resolve("proxysql.cnf");
            Files.writeString(cnfFile, cnf.toString());
            logger.info("Temporary proxysql.cnf file created at: " + cnfFile.toAbsolutePath());

            // Create ConfigMap using proxysql.cnf
            KubernetesUtil.executeCommand(
                    "kubectl", "create", "configmap", "proxysql-configmap",
                    "-n", "user",
                    "--from-file=proxysql.cnf=" + cnfFile.toAbsolutePath()
            );

            // Generate Proxy Service YAML
            logger.info("Loading deployment template: " + PROXY_SERVICE);
            String serviceYaml = Files.readString(Paths.get(PROXY_SERVICE))
                    .replace("${DB_SERVICE}", dbService)
                    .replace("${DB_PORT}", dbPort);

            tempProxySQLService = Files.createTempFile("proxysql-service-", ".yml");
            Files.write(tempProxySQLService, serviceYaml.getBytes());
            logger.info("Temporary proxy service YAML generated at: " + tempProxySQLService);

            // Generate Proxy Deployment YAML
            logger.info("Loading deployment template: " + PROXY_DEPLOYMENT);
            String deploymentYaml = Files.readString(Paths.get(PROXY_DEPLOYMENT));
            tempProxySQLDeployment = Files.createTempFile("proxysql-deployment-", ".yml");
            Files.write(tempProxySQLDeployment, deploymentYaml.getBytes());
            logger.info("Temporary proxy deployment YAML generated at: " + tempProxySQLDeployment);

        } catch (IOException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to generate Cache-Aside (MySQL Proxy) config", e);
        }
    }

    @Override
    public void deployPattern() {
        try {
            // Deploy proxy SQL service
            try {
                KubernetesUtil.applyYaml(tempProxySQLService.toString(), "user");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to apply ProxySQL Service YAML: " + tempProxySQLService, e);
            }

            // Deploy proxy SQL deployment
            try {
                KubernetesUtil.applyYaml(tempProxySQLDeployment.toString(), "user");
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to apply ProxySQL Deployment YAML: " + tempProxySQLDeployment, e);
            }

            logger.info("Cache-Aside Pattern deployed successfully.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Deployment failed for Cache-Aside pattern", e);

        } finally {
            try {
                Files.deleteIfExists(tempProxySQLService);
                Files.deleteIfExists(tempProxySQLDeployment);
            } catch (IOException io) {
                logger.warning("Failed to delete temporary files: " + io.getMessage());
            }
        }
    }

    private void renameBackendService(String serviceName) throws IOException, InterruptedException {
        Path svcPath = Paths.get("svc-" + serviceName + ".yaml");

        // Get original YAML
        KubernetesUtil.getServiceYamlToFile(serviceName, "user", svcPath);

        // Delete the original service
        KubernetesUtil.executeCommand("kubectl", "delete", "svc", serviceName, "-n", "user");

        // Modify the service name
        List<String> lines = Files.readAllLines(svcPath);
        List<String> modifiedLines = new ArrayList<>();
        for (String line : lines) {
            if (line.trim().startsWith("name:")) {
                modifiedLines.add("  name: " + serviceName + "-backend");
            } else {
                modifiedLines.add(line);
            }
        }
        Files.write(svcPath, modifiedLines);

        // Apply updated YAML
        KubernetesUtil.executeCommand("kubectl", "apply", "-f", svcPath.toString());
    }
}