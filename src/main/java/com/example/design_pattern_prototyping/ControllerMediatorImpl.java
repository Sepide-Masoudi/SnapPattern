package com.example.design_pattern_prototyping;

public class ControllerMediatorImpl implements ControllerMediator {

    private static ControllerMediatorImpl instance;

    private PatternBuilderController patternBuilderController;
    private WorkloadController workloadController;
    private MetricsController metricsController;

    private ControllerMediatorImpl() {}

    public static ControllerMediatorImpl getInstance() {
        if (instance == null) {
            instance = new ControllerMediatorImpl();
        }
        return instance;
    }

    @Override
    public void registerPatternBuilderController(PatternBuilderController controller) {
        this.patternBuilderController = controller;
    }

    @Override
    public void registerWorkloadController(WorkloadController controller) {
        this.workloadController = controller;
    }

    @Override
    public void registerMetricsController(MetricsController controller) {
        this.metricsController = controller;
    }

    @Override
    public String getSelectedWorkloadLevel() {
        if (workloadController != null) {
            return workloadController.getSelectedWorkloadLevel();
        }
        return null;
    }

    @Override
    public String getSelectedPattern() {
        if (patternBuilderController != null) {
            return patternBuilderController.getSelectedPattern();
        }
        return null;
    }

    @Override
    public MetricsController getMetricsController() {
        return metricsController;
    }
}
