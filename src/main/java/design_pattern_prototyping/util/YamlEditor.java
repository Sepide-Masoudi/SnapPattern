package design_pattern_prototyping.util;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Logger;

public class YamlEditor {

    private static final Logger logger = Logger.getLogger(YamlEditor.class.getName());

    public static String injectAnnotation(String yaml, Map<String, String> languageMap) throws Exception {
        try {
            Yaml yamlParser = new Yaml();
            Iterable<Object> documents = yamlParser.loadAll(yaml);
            List<Map<String, Object>> updatedDocs = new ArrayList<>();

            for (Object doc : documents) {
                if (!(doc instanceof Map)) {
                    logger.warning("Skipped a document that is not a valid YAML map.");
                    continue;
                }

                Map<String, Object> resource = (Map<String, Object>) doc;
                String kind = (String) resource.get("kind");
                Map<String, Object> metadata = (Map<String, Object>) resource.get("metadata");

                if ("Deployment".equalsIgnoreCase(kind) && metadata != null) {
                    String deploymentName = (String) metadata.get("name");
                    String language = languageMap.get(deploymentName);

                    if (language != null) {
                        try {
                            String annotationKey = switch (language.toLowerCase()) {
                                case "java" -> "instrumentation.opentelemetry.io/inject-java";
                                case "python" -> "instrumentation.opentelemetry.io/inject-python";
                                case "nodejs" -> "instrumentation.opentelemetry.io/inject-nodejs";
                                case ".net" -> "instrumentation.opentelemetry.io/inject-dotnet";
                                case "go (Not available yet)" -> "instrumentation.opentelemetry.io/inject-go";
                                default -> throw new IllegalArgumentException("Unsupported language: " + language);
                            };

                            Map<String, Object> spec = (Map<String, Object>) resource.get("spec");
                            Map<String, Object> template = (Map<String, Object>) spec.get("template");
                            Map<String, Object> tmplMetadata = (Map<String, Object>) template.get("metadata");
                            Map<String, Object> annotations = (Map<String, Object>) tmplMetadata.getOrDefault("annotations", new LinkedHashMap<>());

                            annotations.put(annotationKey, "otel/otel-operator-instrumentation");
                            tmplMetadata.put("annotations", annotations);

                            logger.info("Annotation added to deployment : " + deploymentName);
                        } catch (Exception e) {
                            logger.warning("Failed to add annotation to deployment '" + deploymentName + "': " + e.getMessage());
                        }
                    } else {
                        logger.info("No instrumentation language selected for deployment '" + deploymentName + "'. Skipping.");
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

    public static List<String> extractDeploymentNames(String yaml) throws Exception {
        List<String> deploymentNames = new ArrayList<>();
        try {
            Yaml yamlParser = new Yaml();
            Iterable<Object> documents = yamlParser.loadAll(yaml);
            for (Object doc : documents) {
                if (doc instanceof Map<?, ?> map) {
                    String kind = (String) map.get("kind");
                    if ("Deployment".equalsIgnoreCase(kind)) {
                        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
                        String name = (String) metadata.get("name");
                        if (name != null) {
                            deploymentNames.add(name);
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception("YAML parsing error at extractDeploymentNames: " + e.getMessage(), e);
        }

        return deploymentNames;
    }
}