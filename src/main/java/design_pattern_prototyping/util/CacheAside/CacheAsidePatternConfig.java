package design_pattern_prototyping.util.CacheAside;

import javafx.beans.property.SimpleStringProperty;

public class CacheAsidePatternConfig {
    private final SimpleStringProperty backendService;
    private final SimpleStringProperty cachedEndpoints;

    public CacheAsidePatternConfig(String backendService, String cachedEndpoints) {
        this.backendService = new SimpleStringProperty(backendService);
        this.cachedEndpoints = new SimpleStringProperty(cachedEndpoints);
    }

    public String getBackendService() {
        return backendService.get();
    }

    public String getCachedEndpoints() {
        return cachedEndpoints.get();
    }

    public void setBackendService(String backendService) {
        this.backendService.set(backendService);
    }

    public void setCachedEndpoints(String cachedEndpoints) {
        this.cachedEndpoints.set(cachedEndpoints);
    }

    public SimpleStringProperty backendServiceProperty() {
        return backendService;
    }

    public SimpleStringProperty cachedEndpointsProperty() {
        return cachedEndpoints;
    }
}