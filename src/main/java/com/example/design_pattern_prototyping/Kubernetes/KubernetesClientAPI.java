package com.example.design_pattern_prototyping.Kubernetes;

import io.kubernetes.client.PortForward;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.util.Streams;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class KubernetesClientAPI {

    private static final Logger logger = Logger.getLogger(KubernetesClientAPI.class.getName());
    private final CoreV1Api coreV1Api;
    private final ApiClient apiClient;

    public KubernetesClientAPI(ApiClient apiClient) {
        this.apiClient = apiClient;
        this.coreV1Api = new CoreV1Api(apiClient); // Initialize CoreV1Api using the provided ApiClient
    }

    public Map<String, String> getAllServicesWithNamespaces() throws Exception {
        Map<String, String> servicesWithNamespaces = new HashMap<>();
        var serviceList = coreV1Api.listServiceForAllNamespaces().execute();

        if (serviceList != null) {
            for (var svc : serviceList.getItems()) {
                if (svc.getMetadata() != null && svc.getMetadata().getName() != null) {
                    String name = svc.getMetadata().getName();
                    String namespace = svc.getMetadata().getNamespace();
                    servicesWithNamespaces.put(name, namespace);
                }
            }
        } else {
            logger.warning("No services found in any namespace.");
        }

        return servicesWithNamespaces;
    }

    public Map<String, String> getAllPodsWithNamespaces() throws Exception {
        Map<String, String> podsWithNamespaces = new HashMap<>();
        V1PodList podList = coreV1Api.listPodForAllNamespaces().execute();

        if (podList != null) {
            for (var pod : podList.getItems()) {
                if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                    String podName = pod.getMetadata().getName();
                    String namespace = pod.getMetadata().getNamespace();
                    podsWithNamespaces.put(podName, namespace);
                }
            }
        } else {
            logger.warning("No pods found in any namespace.");
        }
        return podsWithNamespaces;
    }

    public List<String> getPodsInNamespace(String namespace) throws Exception {
        List<String> podNames = new ArrayList<>();
        var podList = coreV1Api.listNamespacedPod(namespace).execute();

        if (podList != null) {
            podList.getItems().forEach(pod -> {
                if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                    podNames.add(pod.getMetadata().getName());
                }
            });
        } else {
            logger.warning("No pods found in namespace: " + namespace);
        }
        return podNames;
    }

    public Map<String, String> getPodDetails(String namespace, String podName) throws Exception {
        var pod = coreV1Api.readNamespacedPod(podName, namespace).execute();
        var details = new HashMap<String, String>();

        if (pod != null && pod.getStatus() != null) {
            assert pod.getMetadata() != null;
            details.put("Name", pod.getMetadata().getName());
            details.put("Status", pod.getStatus().getPhase());
            assert pod.getSpec() != null;
            details.put("Node", pod.getSpec().getNodeName());
            details.put("IP", pod.getStatus().getPodIP());
        }
        return details;
    }

    public List<String> getPodsForService(String namespace, String serviceName) throws Exception {
        V1Service service = coreV1Api.readNamespacedService(serviceName, namespace).execute();

        Map<String, String> selector = service.getSpec().getSelector();
        if (selector == null || selector.isEmpty()) {
            logger.warning("No selector found for service: " + serviceName);
            return new ArrayList<>();
        }

        String labelSelector = selector.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        V1PodList podList = coreV1Api.listNamespacedPod(namespace)
                .labelSelector(labelSelector)
                .execute();

        List<String> podNames = new ArrayList<>();
        if (podList != null) {
            podList.getItems().forEach(pod -> podNames.add(pod.getMetadata().getName()));
        }

        return podNames;
    }

    public String getFirstPodNameForService(String namespace, String serviceName) throws Exception {
        // Get the service object
        V1Service service = coreV1Api.readNamespacedService(serviceName, namespace).execute();

        // Extract the service's selector
        assert service.getSpec() != null;
        Map<String, String> selector = service.getSpec().getSelector();
        if (selector == null || selector.isEmpty()) {
            logger.severe("No selector found for service: " + serviceName);
            return null;
        }

        // Convert selector map to label selector string
        String labelSelector = selector.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        // Build a request object for listing pods
        CoreV1Api.APIlistNamespacedPodRequest request = coreV1Api.listNamespacedPod(namespace)
                .labelSelector(labelSelector);

        // Execute the request and get the list of pods
        V1PodList podList = request.execute();

        if (podList == null || podList.getItems().isEmpty()) {
            logger.severe("No pods found matching selector for service: " + serviceName);
            return null;
        }

        // Return the name of the first pod
        return podList.getItems().get(0).getMetadata().getName();
    }

    public List<String> getServicesInNamespace(String namespace) throws Exception {
        // Use the instance CoreV1Api
        CoreV1Api.APIlistNamespacedServiceRequest request = coreV1Api.listNamespacedService(namespace);
        List<String> serviceNames = new ArrayList<>();
        var serviceList = request.execute();

        if (serviceList != null) {
            serviceList.getItems().forEach(service -> {
                if (service.getMetadata() != null && service.getMetadata().getName() != null) {
                    serviceNames.add(service.getMetadata().getName());
                }
            });
        } else {
            logger.severe("No services found in namespace: " + namespace);
        }
        return serviceNames;
    }

    public void startPortForwarding(String namespace, String serviceName, int localPort, int targetPort) {
        try {
            // Resolve the service to its corresponding pod
            String podName = getFirstPodNameForService(namespace, serviceName);
            if (podName == null) {
                logger.severe("No pod found for service: " + serviceName);
                return;
            }

            logger.info("Found pod for service: " + podName);

            // Start port forwarding
            PortForward portForward = new PortForward(apiClient);
            List<Integer> ports = List.of(targetPort);

            logger.info("Starting port-forwarding for pod: " + podName);
            PortForward.PortForwardResult result = portForward.forward(namespace, podName, ports);

            // Use a separate thread to keep the port-forwarding connection alive
            Thread portForwardThread = new Thread(() -> {
                try (Socket socket = new Socket("127.0.0.1", localPort)) {
                    logger.info("Port-forwarding established on localhost:" + localPort);
                    Streams.copy(result.getInputStream(targetPort), socket.getOutputStream());
                    Streams.copy(socket.getInputStream(), result.getOutboundStream(targetPort));
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error maintaining the port-forward connection", e);
                }
            });

            portForwardThread.setDaemon(true); // Ensure the thread terminates when the main program exits
            portForwardThread.start();

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to start port-forwarding", e);
        }
    }

}
