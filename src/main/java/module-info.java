module com.example.design_pattern_prototyping {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires org.yaml.snakeyaml;
    requires java.net.http;
    requires org.json;
    requires com.fasterxml.jackson.databind;
    requires java.logging;
    requires io.kubernetes.client.java;
    requires io.kubernetes.client.java.api;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires org.bouncycastle.pkix;
    requires annotations;
    requires com.google.gson;

    opens design_pattern_prototyping to javafx.fxml;
    exports design_pattern_prototyping;
    exports design_pattern_prototyping.pattern_generator;
    opens design_pattern_prototyping.pattern_generator to javafx.fxml;
    exports design_pattern_prototyping.Kubernetes;
    opens design_pattern_prototyping.Kubernetes to javafx.fxml;
    exports design_pattern_prototyping.controller;
    opens design_pattern_prototyping.controller to javafx.fxml;
}
