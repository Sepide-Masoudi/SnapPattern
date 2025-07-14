package design_pattern_prototyping.util.CacheAsideSQL;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CacheAsideSQLDialogs {

    public static List<ProxySQLUser> showDBUserDialog(Stage parentStage) {
        ObservableList<ProxySQLUser> userList = FXCollections.observableArrayList();

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(parentStage);
        dialog.setTitle("Configure MySQL Users");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));

        TableView<ProxySQLUser> table = new TableView<>(userList);
        TableColumn<ProxySQLUser, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(param -> param.getValue().usernameProperty());

        TableColumn<ProxySQLUser, String> passwordCol = new TableColumn<>("Password");
        passwordCol.setCellValueFactory(param -> param.getValue().passwordProperty());

        table.getColumns().addAll(usernameCol, passwordCol);

        Button addButton = new Button("Add User");
        addButton.setOnAction(e -> {
            Dialog<ProxySQLUser> userDialog = new Dialog<>();
            userDialog.setTitle("Add MySQL User");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField usernameField = new TextField();
            usernameField.setPromptText("e.g., teauser");

            TextField passwordField = new TextField();
            passwordField.setPromptText("e.g., teapassword");

            grid.add(new Label("Username:"), 0, 0);
            grid.add(usernameField, 1, 0);
            grid.add(new Label("Password:"), 0, 1);
            grid.add(passwordField, 1, 1);

            userDialog.getDialogPane().setContent(grid);
            userDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            userDialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    return new ProxySQLUser(usernameField.getText(), passwordField.getText());
                }
                return null;
            });

            userDialog.showAndWait().ifPresent(userList::add);
        });

        Button doneButton = new Button("Done");
        doneButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(table, addButton, doneButton);
        dialog.setScene(new Scene(vbox));
        dialog.showAndWait();

        return new ArrayList<>(userList);
    }

    public static List<ProxySQLRule> showQueryRuleDialog(Stage parentStage) {
        ObservableList<ProxySQLRule> ruleList = FXCollections.observableArrayList();

        Stage dialog = new Stage();
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(parentStage);
        dialog.setTitle("Configure Query Rules");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));

        TableView<ProxySQLRule> table = new TableView<>(ruleList);
        TableColumn<ProxySQLRule, String> patternCol = new TableColumn<>("Regex Pattern");
        patternCol.setCellValueFactory(param -> param.getValue().regexPatternProperty());

        TableColumn<ProxySQLRule, String> ttlCol = new TableColumn<>("Cache TTL (ms)");
        ttlCol.setCellValueFactory(param -> param.getValue().ttlProperty());

        table.getColumns().addAll(patternCol, ttlCol);

        Button addButton = new Button("Add Rule");
        addButton.setOnAction(e -> {
            Dialog<ProxySQLRule> ruleDialog = new Dialog<>();
            ruleDialog.setTitle("Add Query Rule");

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField patternField = new TextField();
            patternField.setPromptText("^SELECT.*");

            TextField ttlField = new TextField();
            ttlField.setPromptText("e.g., 10000");

            grid.add(new Label("Regex Pattern:"), 0, 0);
            grid.add(patternField, 1, 0);
            grid.add(new Label("Cache TTL (ms):"), 0, 1);
            grid.add(ttlField, 1, 1);

            ruleDialog.getDialogPane().setContent(grid);
            ruleDialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            ruleDialog.setResultConverter(button -> {
                if (button == ButtonType.OK) {
                    return new ProxySQLRule(patternField.getText(), ttlField.getText());
                }
                return null;
            });

            ruleDialog.showAndWait().ifPresent(ruleList::add);
        });

        Button doneButton = new Button("Done");
        doneButton.setOnAction(e -> dialog.close());

        vbox.getChildren().addAll(table, addButton, doneButton);
        dialog.setScene(new Scene(vbox));
        dialog.showAndWait();

        return new ArrayList<>(ruleList);
    }

    public static Optional<ProxySQLUser> showEditUserDialog(ProxySQLUser user) {
        Dialog<ProxySQLUser> dialog = new Dialog<>();
        dialog.setTitle("Edit MySQL User");

        TextField usernameField = new TextField(user.getUsername());
        TextField passwordField = new TextField(user.getPassword());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(passwordField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                user.setUsername(usernameField.getText().trim());
                user.setPassword(passwordField.getText().trim());
                return user;
            }
            return null;
        });

        return dialog.showAndWait();
    }

    public static Optional<ProxySQLRule> showEditRuleDialog(ProxySQLRule rule) {
        Dialog<ProxySQLRule> dialog = new Dialog<>();
        dialog.setTitle("Edit Query Rule");

        TextField patternField = new TextField(rule.getRegexPattern());
        TextField ttlField = new TextField(rule.getTtl());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        grid.add(new Label("Regex Pattern:"), 0, 0);
        grid.add(patternField, 1, 0);
        grid.add(new Label("Cache TTL (ms):"), 0, 1);
        grid.add(ttlField, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                rule.setRegexPattern(patternField.getText().trim());
                rule.setTtl(ttlField.getText().trim());
                return rule;
            }
            return null;
        });

        return dialog.showAndWait();
    }
}