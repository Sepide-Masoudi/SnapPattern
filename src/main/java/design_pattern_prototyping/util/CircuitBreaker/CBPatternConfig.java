package design_pattern_prototyping.util.CircuitBreaker;

public class CBPatternConfig {
    private String serviceName;
    private String routePrefix;
    private int port;
    private int maxConnections;
    private int maxPendingRequests;
    private int maxRequests;
    private int retryAttempts;
    private String perTryTimeout;

    public CBPatternConfig() {}

    public CBPatternConfig(String serviceName, String routePrefix, int port,
                                       int maxConnections, int maxPendingRequests, int maxRequests,
                                       int retryAttempts, String perTryTimeout) {
        this.serviceName = serviceName;
        this.routePrefix = routePrefix;
        this.port = port;
        this.maxConnections = maxConnections;
        this.maxPendingRequests = maxPendingRequests;
        this.maxRequests = maxRequests;
        this.retryAttempts = retryAttempts;
        this.perTryTimeout = perTryTimeout;
    }

    // Getters and setters
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getRoutePrefix() { return routePrefix; }
    public void setRoutePrefix(String routePrefix) { this.routePrefix = routePrefix; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public int getMaxConnections() { return maxConnections; }
    public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

    public int getMaxPendingRequests() { return maxPendingRequests; }
    public void setMaxPendingRequests(int maxPendingRequests) { this.maxPendingRequests = maxPendingRequests; }

    public int getMaxRequests() { return maxRequests; }
    public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public String getPerTryTimeout() { return perTryTimeout; }
    public void setPerTryTimeout(String perTryTimeout) { this.perTryTimeout = perTryTimeout; }
}
