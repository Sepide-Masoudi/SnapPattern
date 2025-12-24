package design_pattern_prototyping.Monitoring;

//import com.example.design_pattern_prototyping.Kubernetes.KubernetesClientAPI;
//import io.kubernetes.client.openapi.ApiClient;
//import io.kubernetes.client.util.Config;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

// TODO Replace HttpURLConnection library like Apache HttpClient
// TODO add retries, and timeouts.
// TODO Add Logging
public class PrometheusClient {

    //TODO SEPIDEH
    private static final String baseUrl = "http://192.168.121.2:30090";//"http://192.168.49.2:30090";//"http://192.168.85.2:30090";//"http://192.168.76.2:30090";//"http://192.168.67.2:30090";//"http://192.168.58.2:30090";
    private static final Logger logger = Logger.getLogger(PrometheusClient.class.getName());

    /**
    private static final String NAMESPACE = "monitoring";
    private static final String SERVICE_NAME = "prometheus-kube-prometheus-prometheus";
    private static final int LOCAL_PORT = 9090;
    private static final int TARGET_PORT = 9090;
    private static Process prometheusProcess;

    public static void startPortForwarding2() {
        try {
            // Initialize the Kubernetes API client
            ApiClient client = Config.defaultClient();
            KubernetesClientAPI = new KubernetesClientAPI(client);

            // Call the generalized port-forwarding method
            kubernetesClientAPI.startPortForwarding(NAMESPACE, SERVICE_NAME, LOCAL_PORT, TARGET_PORT);

        } catch (Exception e) {
            logger.severe("Failed to initialize port forwarding: " + e.getMessage());
        }
    }

    public static void startPortForwarding() {
        new Thread(() -> {
            try {
                prometheusProcess = new ProcessBuilder(
                        "kubectl", "port-forward", "svc/prometheus-kube-prometheus-prometheus", "9090:9090", "-n", "monitoring"
                ).start();
                logger.info("Prometheus port forwarding started on http://localhost:9090");

                prometheusProcess.waitFor();
            } catch (IOException | InterruptedException e) {
                logger.log(Level.SEVERE, "Error starting Prometheus port forwarding", e);
            }
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (prometheusProcess != null && prometheusProcess.isAlive()) {
                logger.info("Terminating Prometheus port forwarding...");
                prometheusProcess.destroy();
                try {
                    prometheusProcess.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }));
    }
     **/
    public String queryPrometheus(String query) throws Exception {
        String url = baseUrl + "/api/v1/query?query=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            throw new Exception("Failed to query Prometheus: HTTP code " + responseCode);
        }
    }

    public String queryRange(String promql, String start, String end, String step) throws Exception {
        String url = String.format(
                "%s/api/v1/query_range?query=%s&start=%s&end=%s&step=%s",
                baseUrl,
                URLEncoder.encode(promql, StandardCharsets.UTF_8),
                URLEncoder.encode(start, StandardCharsets.UTF_8),
                URLEncoder.encode(end, StandardCharsets.UTF_8),
                URLEncoder.encode(step, StandardCharsets.UTF_8)
        );
        logger.info("Sending range query to Prometheus: " + url);

        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestMethod("GET");

        int responseCode = con.getResponseCode();
        if (responseCode == 200) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } else {
            throw new Exception("Failed to query Prometheus: HTTP code " + responseCode);
        }
    }
}