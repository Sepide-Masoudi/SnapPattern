package design_pattern_prototyping.controller;

public interface ControllerMediator {
    void registerPatternBuilderController(PatternController controller);
    void registerWorkloadController(WorkloadController controller);
    void registerMetricsController(MetricsController controller);

    String getSelectedWorkloadLevel();
    String getSelectedPattern();

    MetricsController getMetricsController();
}