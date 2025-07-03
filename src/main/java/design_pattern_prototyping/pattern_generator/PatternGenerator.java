package design_pattern_prototyping.pattern_generator;

import java.util.List;
import java.util.Map;

public interface PatternGenerator {
    void generatePattern(Map<String, String> parameters);

    default void generatePattern(List<Map<String, String>> parameterList) {
        throw new UnsupportedOperationException("Not implemented for this pattern.");
    }

    void deployPattern();
}
