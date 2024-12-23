package com.example.design_pattern_prototyping.pattern_generator;

import java.util.Map;

public interface PatternGenerator {
    void generatePattern(String filePath, Map<String, String> parameters);
    String getYamlFilePath();
    void deployPattern();
}
