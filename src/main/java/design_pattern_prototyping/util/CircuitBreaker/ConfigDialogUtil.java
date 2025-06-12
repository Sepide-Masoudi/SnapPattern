package design_pattern_prototyping.util.CircuitBreaker;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.Optional;

public class ConfigDialogUtil {

    public static Optional<CBPatternConfig> showAddDialog() {
        Dialog<CBPatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Add Circuit Breaker Configuration");

        GridPane grid = new GridPane();
        TextField serviceField = new TextField();
        TextField prefixField = new TextField("/");
        TextField portField = new TextField("80");
        TextField connField = new TextField("100");
        TextField pendField = new TextField("20");
        TextField reqField = new TextField("1");
        TextField retryField = new TextField("2");
        TextField timeoutField = new TextField("1s");

        grid.addRow(0, new Label("Service Name:"), serviceField);
        grid.addRow(1, new Label("Route Prefix:"), prefixField);
        grid.addRow(2, new Label("Port:"), portField);
        grid.addRow(3, new Label("Max Connections:"), connField);
        grid.addRow(4, new Label("Max Pending Requests:"), pendField);
        grid.addRow(5, new Label("Max Requests:"), reqField);
        grid.addRow(6, new Label("Retry Attempts:"), retryField);
        grid.addRow(7, new Label("Per-Try Timeout:"), timeoutField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    return new CBPatternConfig(
                            serviceField.getText().trim(),
                            prefixField.getText().trim(),
                            Integer.parseInt(portField.getText().trim()),
                            Integer.parseInt(connField.getText().trim()),
                            Integer.parseInt(pendField.getText().trim()),
                            Integer.parseInt(reqField.getText().trim()),
                            Integer.parseInt(retryField.getText().trim()),
                            timeoutField.getText().trim()
                    );
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public static Optional<CBPatternConfig> showEditDialog(CBPatternConfig config) {
        Dialog<CBPatternConfig> dialog = new Dialog<>();
        dialog.setTitle("Edit Circuit Breaker Configuration");

        GridPane grid = new GridPane();
        TextField serviceField = new TextField(config.getServiceName());
        TextField prefixField = new TextField(config.getRoutePrefix());
        TextField portField = new TextField(String.valueOf(config.getPort()));
        TextField connField = new TextField(String.valueOf(config.getMaxConnections()));
        TextField pendField = new TextField(String.valueOf(config.getMaxPendingRequests()));
        TextField reqField = new TextField(String.valueOf(config.getMaxRequests()));
        TextField retryField = new TextField(String.valueOf(config.getRetryAttempts()));
        TextField timeoutField = new TextField(config.getPerTryTimeout());

        grid.addRow(0, new Label("Service Name:"), serviceField);
        grid.addRow(1, new Label("Route Prefix:"), prefixField);
        grid.addRow(2, new Label("Port:"), portField);
        grid.addRow(3, new Label("Max Connections:"), connField);
        grid.addRow(4, new Label("Max Pending Requests:"), pendField);
        grid.addRow(5, new Label("Max Requests:"), reqField);
        grid.addRow(6, new Label("Retry Attempts:"), retryField);
        grid.addRow(7, new Label("Per-Try Timeout:"), timeoutField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                try {
                    config.setServiceName(serviceField.getText().trim());
                    config.setRoutePrefix(prefixField.getText().trim());
                    config.setPort(Integer.parseInt(portField.getText().trim()));
                    config.setMaxConnections(Integer.parseInt(connField.getText().trim()));
                    config.setMaxPendingRequests(Integer.parseInt(pendField.getText().trim()));
                    config.setMaxRequests(Integer.parseInt(reqField.getText().trim()));
                    config.setRetryAttempts(Integer.parseInt(retryField.getText().trim()));
                    config.setPerTryTimeout(timeoutField.getText().trim());
                    return config;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        });

        return dialog.showAndWait();
    }
}