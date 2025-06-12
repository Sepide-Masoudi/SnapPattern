package design_pattern_prototyping.util.AsyncRequestReply;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class ConfigDialogUtil {

    public static Optional<AsyncPatternConfig> showAddDialog() {
        Dialog<AsyncPatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Add Async Request Reply Configuration");

        TextField serviceField = new TextField();
        TextField endpointField = new TextField();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Service Name:"), 0, 0);
        grid.add(serviceField, 1, 0);
        grid.add(new Label("Endpoint Path:"), 0, 1);
        grid.add(endpointField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                String service = serviceField.getText().trim();
                String endpoint = endpointField.getText().trim();
                if (!service.isEmpty() && !endpoint.isEmpty()) {
                    return new AsyncPatternConfig(service, endpoint);
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public static Optional<AsyncPatternConfig> showEditDialog(AsyncPatternConfig config) {
        Dialog<AsyncPatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Edit Async Request Reply Configuration");

        TextField serviceField = new TextField(config.getServiceName());
        TextField endpointField = new TextField(config.getEndpointPath());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Service Name:"), 0, 0);
        grid.add(serviceField, 1, 0);
        grid.add(new Label("Endpoint Path:"), 0, 1);
        grid.add(endpointField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                config.setServiceName(serviceField.getText().trim());
                config.setEndpointPath(endpointField.getText().trim());
                return config;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}