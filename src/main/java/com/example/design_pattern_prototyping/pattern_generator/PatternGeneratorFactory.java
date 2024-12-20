package com.example.design_pattern_prototyping.pattern_generator;

import java.util.HashMap;
import java.util.Map;

public class PatternGeneratorFactory {

    private static final Map<String, AsyncRequestReplyGenerator> generators = new HashMap<>();

    static {
        generators.put("Async Request Reply", new AsyncRequestReplyGenerator());
        //generators.put("New Pattern 1", new NewPattern1Generator());
        // Add other patterns as needed
    }

    public static AsyncRequestReplyGenerator getGenerator(String patternName) {
        return generators.get(patternName);
    }
}
