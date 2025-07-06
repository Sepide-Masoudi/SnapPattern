package design_pattern_prototyping.util.CacheAside;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class ConfigDialogUtil {

    public static Optional<CacheAsidePatternConfig> showAddDialog() {
        Dialog<CacheAsidePatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Add Cache Aside Configuration");

        TextField backendServiceField = new TextField();
        TextField backendPortField = new TextField();
        TextField cachedEndpointsField = new TextField();
        TextField cacheTTLField = new TextField();
        TextField maxConnectionsField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Backend Service:"), 0, 0);
        grid.add(backendServiceField, 1, 0);
        grid.add(new Label("Backend Port:"), 0, 1);
        grid.add(backendPortField, 1, 1);
        grid.add(new Label("Cached Endpoints (comma-separated):"), 0, 2);
        grid.add(cachedEndpointsField, 1, 2);
        grid.add(new Label("Cache TTL (seconds):"), 0, 3);
        grid.add(cacheTTLField, 1, 3);
        grid.add(new Label("Max Connections:"), 0, 4);
        grid.add(maxConnectionsField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String backend = backendServiceField.getText().trim();
                String port = backendPortField.getText().trim();
                String endpoints = cachedEndpointsField.getText().trim();
                String cacheTTL = cacheTTLField.getText().trim();
                String maxConnections = maxConnectionsField.getText().trim();
                if (!backend.isEmpty() && !port.isEmpty() && !endpoints.isEmpty()
                        && !cacheTTL.isEmpty() && !maxConnections.isEmpty()) {
                    return new CacheAsidePatternConfig(backend, port, endpoints, cacheTTL, maxConnections);
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public static Optional<CacheAsidePatternConfig> showEditDialog(CacheAsidePatternConfig config) {
        Dialog<CacheAsidePatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Edit Cache Aside Configuration");

        TextField backendServiceField = new TextField(config.getBackendService());
        TextField backendPortField = new TextField(config.getBackendPort());
        TextField cachedEndpointsField = new TextField(config.getCachedEndpoints());
        TextField cacheTTLField = new TextField(config.getCacheTTL());
        TextField maxConnectionsField = new TextField(config.getMaxConnections());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Backend Service:"), 0, 0);
        grid.add(backendServiceField, 1, 0);
        grid.add(new Label("Backend Port:"), 0, 1);
        grid.add(backendPortField, 1, 1);
        grid.add(new Label("Cached Endpoints (comma-separated):"), 0, 2);
        grid.add(cachedEndpointsField, 1, 2);
        grid.add(new Label("Cache TTL (seconds):"), 0, 3);
        grid.add(cacheTTLField, 1, 3);
        grid.add(new Label("Max Connections:"), 0, 4);
        grid.add(maxConnectionsField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                config.setBackendService(backendServiceField.getText().trim());
                config.setBackendPort(backendPortField.getText().trim());
                config.setCachedEndpoints(cachedEndpointsField.getText().trim());
                config.setCacheTTL(cacheTTLField.getText().trim());
                config.setMaxConnections(maxConnectionsField.getText().trim());
                return config;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}