package com.example.design_pattern_prototyping.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public class YamlEditor {

    private static final Logger logger = Logger.getLogger(YamlEditor.class.getName());

    public static String injectAnnotation(String yaml, String language) throws Exception {
        String annotationKey = switch (language.toLowerCase()) {
            case "java" -> "instrumentation.opentelemetry.io/inject-java";
            case "python" -> "instrumentation.opentelemetry.io/inject-python";
            case "nodejs" -> "instrumentation.opentelemetry.io/inject-nodejs";
            case ".net" -> "instrumentation.opentelemetry.io/inject-dotnet";
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };

        try {
            Yaml yamlParser = new Yaml();
            Iterable<Object> documents = yamlParser.loadAll(yaml);
            List<Map<String, Object>> updatedDocs = new ArrayList<>();

            for (Object doc : documents) {
                if (!(doc instanceof Map)) continue;

                Map<String, Object> resource = (Map<String, Object>) doc;
                String kind = (String) resource.get("kind");

                if ("Deployment".equalsIgnoreCase(kind)) {
                    try {
                        Map<String, Object> spec = (Map<String, Object>) resource.get("spec");
                        Map<String, Object> template = (Map<String, Object>) spec.get("template");
                        Map<String, Object> metadata = (Map<String, Object>) template.get("metadata");
                        Map<String, Object> annotations = (Map<String, Object>) metadata.getOrDefault("annotations", new LinkedHashMap<>());

                        annotations.put(annotationKey, "otel/otel-operator-instrumentation");
                        metadata.put("annotations", annotations);

                        logger.info("Annotation added to deployment: " + resource.get("metadata"));
                    } catch (Exception e) {
                        logger.warning("Failed to inject annotation into a deployment: " + e.getMessage());
                    }
                }

                updatedDocs.add(resource);
            }

            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setExplicitStart(true);
            Yaml yamlWriter = new Yaml(options);

            return updatedDocs.stream()
                    .map(yamlWriter::dump)
                    .collect(Collectors.joining("\n"));

        } catch (Exception e) {
            logger.severe("Failed to inject OpenTelemetry annotation: " + e.getMessage());
            throw new Exception("Annotation injection failed: " + e.getMessage(), e);
        }
    }
}