package design_pattern_prototyping.util;

import javafx.beans.property.SimpleStringProperty;

public class DeploymentMapping {
    private final SimpleStringProperty deploymentName;
    private final SimpleStringProperty language;

    public DeploymentMapping(String deploymentName) {
        this.deploymentName = new SimpleStringProperty(deploymentName);
        this.language = new SimpleStringProperty("");
    }

    public String getDeploymentName() {
        return deploymentName.get();
    }

    public void setDeploymentName(String name) {
        this.deploymentName.set(name);
    }

    public String getLanguage() {
        return language.get();
    }

    public void setLanguage(String lang) {
        this.language.set(lang);
    }

    public SimpleStringProperty deploymentNameProperty() {
        return deploymentName;
    }

    public SimpleStringProperty languageProperty() {
        return language;
    }
}