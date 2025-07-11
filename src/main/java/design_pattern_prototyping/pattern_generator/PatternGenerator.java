package design_pattern_prototyping.pattern_generator;

import java.util.List;
import java.util.Map;

public interface PatternGenerator {
    void generatePattern(Map<String, String> parameters);

    default void generatePattern(List<Map<String, String>> parameterList) {
        throw new UnsupportedOperationException("Not implemented for this pattern.");
    }

<<<<<<< HEAD
    void deployPattern() throws InterruptedException;
    String getYamlFilePath();
=======
    void deployPattern();
>>>>>>> 6d246dd86bd8744473a8666ed60ed311e98d9c38
}
