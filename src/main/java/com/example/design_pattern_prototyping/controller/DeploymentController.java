package com.example.design_pattern_prototyping.controller;

import com.example.design_pattern_prototyping.util.DeploymentMapping;
import com.example.design_pattern_prototyping.util.YamlEditor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DeploymentController {

    @FXML private TableView<DeploymentMapping> deploymentTable;
    @FXML private TableColumn<DeploymentMapping, String> nameColumn;
    @FXML private TableColumn<DeploymentMapping, String> languageColumn;
    @FXML private Button applyButton;
    @FXML private ComboBox<String> languageComboBox;


    private final ObservableList<DeploymentMapping> deploymentData = FXCollections.observableArrayList();
    private Runnable onDeployConfirmed;

    private File originalYamlFile;
    private File instrumentedYamlFile;

    public void initialize() {
        nameColumn.setCellValueFactory(cellData -> cellData.getValue().deploymentNameProperty());
        languageColumn.setCellValueFactory(cellData -> cellData.getValue().languageProperty());
        languageColumn.setCellFactory(ComboBoxTableCell.forTableColumn("Java", "Python", "NodeJS", ".NET"));

        deploymentTable.setItems(deploymentData);
        deploymentTable.setEditable(true);

        languageColumn.setOnEditCommit(event -> {
            event.getRowValue().setLanguage(event.getNewValue());
            validateDeployButton();
        });

        deploymentData.addListener((javafx.collections.ListChangeListener<DeploymentMapping>) c -> validateDeployButton());
    }

    public void setDeployments(List<String> deploymentNames) {
        deploymentData.clear();
        deploymentNames.forEach(name -> deploymentData.add(new DeploymentMapping(name)));
        validateDeployButton();
    }

    public void setOriginalYamlFile(File file) {
        this.originalYamlFile = file;
    }

    public File getInstrumentedamlFile() {
        return instrumentedYamlFile;
    }

    public Map<String, String> getConfirmedLanguageMap() {
        return deploymentData.stream()
                .collect(Collectors.toMap(DeploymentMapping::getDeploymentName, DeploymentMapping::getLanguage));
    }

    private void validateDeployButton() {
        boolean allSelected = deploymentData.stream().allMatch(d -> d.getLanguage() != null && !d.getLanguage().isEmpty());
        applyButton.setDisable(!allSelected);
    }

    @FXML
    private void applyLanguageToAll() {
        String selectedLanguage = languageComboBox.getValue(); // assuming you have a ComboBox for global selection
        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            return;
        }

        for (DeploymentMapping mapping : deploymentData) {
            mapping.setLanguage(selectedLanguage);
        }

        deploymentTable.refresh();
        validateDeployButton();
    }

    @FXML
    private void handleApply() {
        try {
            String originalYaml = Files.readString(originalYamlFile.toPath());
            Map<String, String> languageMap = getConfirmedLanguageMap();
            String updatedYaml = YamlEditor.injectAnnotation(originalYaml, languageMap);

            Path tempPath = Files.createTempFile("instrumented-", ".yaml");
            Files.writeString(tempPath, updatedYaml);
            tempPath.toFile().deleteOnExit();
            instrumentedYamlFile = tempPath.toFile();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR, "Failed to inject annotations: " + e.getMessage()).showAndWait();
        }

        if (onDeployConfirmed != null) {
            onDeployConfirmed.run();
        }

        ((Stage) applyButton.getScene().getWindow()).close();
    }

}