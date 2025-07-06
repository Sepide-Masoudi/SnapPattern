package design_pattern_prototyping.util.CacheAside;

import javafx.beans.property.SimpleStringProperty;

public class CacheAsidePatternConfig {
    private final SimpleStringProperty backendService;
    private final SimpleStringProperty backendPort;
    private final SimpleStringProperty cachedEndpoints;
    private final SimpleStringProperty cacheTTL;
    private final SimpleStringProperty maxConnections;

    public CacheAsidePatternConfig(String backendService, String backendPort, String cachedEndpoints,
                                   String cacheTTL, String maxConnections) {
        this.backendService = new SimpleStringProperty(backendService);
        this.backendPort = new SimpleStringProperty(backendPort);
        this.cachedEndpoints = new SimpleStringProperty(cachedEndpoints);
        this.cacheTTL = new SimpleStringProperty(cacheTTL);
        this.maxConnections = new SimpleStringProperty(maxConnections);
    }

    public String getBackendService() {
        return backendService.get();
    }

    public String getBackendPort() {
        return backendPort.get();
    }

    public String getCachedEndpoints() {
        return cachedEndpoints.get();
    }

    public String getCacheTTL() {
        return cacheTTL.get();
    }

    public String getMaxConnections() {
        return maxConnections.get();
    }

    public void setBackendService(String backendService) {
        this.backendService.set(backendService);
    }

    public void setBackendPort(String backendPort) {
        this.backendPort.set(backendPort);
    }

    public void setCachedEndpoints(String cachedEndpoints) {
        this.cachedEndpoints.set(cachedEndpoints);
    }

    public void setCacheTTL(String cacheTTL) {
        this.cacheTTL.set(cacheTTL);
    }

    public void setMaxConnections(String maxConnections) {
        this.maxConnections.set(maxConnections);
    }

    public SimpleStringProperty backendServiceProperty() {
        return backendService;
    }

    public SimpleStringProperty backendPortProperty() {
        return backendPort;
    }

    public SimpleStringProperty cachedEndpointsProperty() {
        return cachedEndpoints;
    }

    public SimpleStringProperty cacheTTLProperty() {
        return cacheTTL;
    }

    public SimpleStringProperty maxConnectionsProperty() {
        return maxConnections;
    }
}