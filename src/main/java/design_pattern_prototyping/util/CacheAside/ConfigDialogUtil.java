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
        TextField cachedEndpointsField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Backend Service:"), 0, 0);
        grid.add(backendServiceField, 1, 0);
        grid.add(new Label("Cached Endpoints (comma-separated):"), 0, 1);
        grid.add(cachedEndpointsField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String backend = backendServiceField.getText().trim();
                String endpoints = cachedEndpointsField.getText().trim();
                if (!backend.isEmpty() && !endpoints.isEmpty()) {
                    return new CacheAsidePatternConfig(backend, endpoints);
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
        TextField cachedEndpointsField = new TextField(config.getCachedEndpoints());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Backend Service:"), 0, 0);
        grid.add(backendServiceField, 1, 0);
        grid.add(new Label("Cached Endpoints (comma-separated):"), 0, 1);
        grid.add(cachedEndpointsField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                config.setBackendService(backendServiceField.getText().trim());
                config.setCachedEndpoints(cachedEndpointsField.getText().trim());
                return config;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}