package com.example.design_pattern_prototyping;

public interface ControllerMediator {
    void registerPatternBuilderController(PatternController controller);
    void registerWorkloadController(WorkloadController controller);
    void registerMetricsController(MetricsController controller);

    String getSelectedWorkloadLevel();
    String getSelectedPattern();

    MetricsController getMetricsController();
}