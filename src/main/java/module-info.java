module com.example.design_pattern_prototyping {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.yaml.snakeyaml;
    requires java.net.http;
    requires org.json;

    opens com.example.design_pattern_prototyping to javafx.fxml;
    exports com.example.design_pattern_prototyping;
    exports com.example.design_pattern_prototyping.pattern_generator;
    opens com.example.design_pattern_prototyping.pattern_generator to javafx.fxml;
}
