package design_pattern_prototyping.util.AsyncRequestReply;

import javafx.beans.property.SimpleStringProperty;

public class AsyncPatternConfig {
    private final SimpleStringProperty serviceName;
    private final SimpleStringProperty endpointPath;

    public AsyncPatternConfig(String serviceName, String endpointPath) {
        this.serviceName = new SimpleStringProperty(serviceName);
        this.endpointPath = new SimpleStringProperty(endpointPath);
    }

    public String getServiceName() { return serviceName.get(); }
    public String getEndpointPath() { return endpointPath.get(); }

    public void setServiceName(String name) { this.serviceName.set(name); }
    public void setEndpointPath(String path) { this.endpointPath.set(path); }

    public SimpleStringProperty serviceNameProperty() { return serviceName; }
    public SimpleStringProperty endpointPathProperty() { return endpointPath; }
}