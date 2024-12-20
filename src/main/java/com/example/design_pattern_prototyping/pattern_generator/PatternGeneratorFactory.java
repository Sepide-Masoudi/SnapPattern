package com.example.design_pattern_prototyping.pattern_generator;

import java.util.HashMap;
import java.util.Map;

public class PatternGeneratorFactory {

    private static final Map<String, PatternGenerator> generators = new HashMap<>();

    static {
        generators.put("Async Request Reply", new AsyncRequestReplyGenerator());
        generators.put("Gateway Offloading", new GatewayOffloadingGenerator());
        // Add other patterns as needed
    }

    public static PatternGenerator getGenerator(String patternName) {
        PatternGenerator generator = generators.get(patternName);
        if (generator == null) {
            throw new IllegalArgumentException("No generator found for pattern: " + patternName);
        }
        return generator;
    }
}
