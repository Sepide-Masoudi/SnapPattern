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


    opens com.example.design_pattern_prototyping to javafx.fxml;
    exports com.example.design_pattern_prototyping;
    exports com.example.design_pattern_prototyping.pattern_generator;
    opens com.example.design_pattern_prototyping.pattern_generator to javafx.fxml;
    exports com.example.design_pattern_prototyping.Kubernetes;
    opens com.example.design_pattern_prototyping.Kubernetes to javafx.fxml;
}
