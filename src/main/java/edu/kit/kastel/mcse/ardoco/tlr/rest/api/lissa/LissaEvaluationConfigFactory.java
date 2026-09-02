/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.rest.api.lissa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.kit.kastel.mcse.ardoco.tlr.rest.api.exception.ArdocoException;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.EvaluationConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.GoldStandardConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact.ArtifactType;

@Component
public class LissaEvaluationConfigFactory {

    private static final Logger logger = LoggerFactory.getLogger(LissaEvaluationConfigFactory.class);

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Builds a LiSSA evaluation configuration from the request's input files/types, optionally
     * completed with a caller-supplied partial config for the non-structural pipeline modules.
     * <p>
     * {@code cache_dir}, {@code gold_standard_configuration}, {@code source_artifact_provider}, and
     * {@code target_artifact_provider} are always derived from the request and can never be
     * overridden via {@code additionalConfigJson}, even if present there.
     *
     * @param additionalConfigJson optional partial EvaluationConfiguration JSON (same schema as
     *                             LiSSA's own config files) to override module defaults; may be null/blank
     */
    public EvaluationConfiguration build(List<File> sourceFiles, ArtifactType sourceType, List<File> targetFiles, ArtifactType targetType, Path workingDir,
            @Nullable String additionalConfigJson) {
        Path sourceDir = copyIntoDirectory(sourceFiles, workingDir.resolve("source"));
        Path targetDir = copyIntoDirectory(targetFiles, workingDir.resolve("target"));

        ModuleConfiguration sourceProvider = new ModuleConfiguration("text", new HashMap<>(Map.of("path", sourceDir.toString(), "artifact_type", sourceType
                .name())));
        ModuleConfiguration targetProvider = new ModuleConfiguration("text", new HashMap<>(Map.of("path", targetDir.toString(), "artifact_type", targetType
                .name())));

        GoldStandardConfiguration goldStandard = new GoldStandardConfiguration(
                "src/main/java/edu/kit/kastel/mcse" + "/ardoco/tlr/rest/api/lissa/emptyFile" + ".csv", false, false);

        EvaluationConfiguration config = buildDefaults();

        if (additionalConfigJson != null) {
            config = applyOverrides(config, additionalConfigJson);
        }

        // Guarantee: request-derived fields always win, regardless of what applyOverrides did above.
        return config.withCacheDir(workingDir.resolve("cache").toString())
                .withGoldStandardConfiguration(goldStandard)
                .withSourceArtifactProvider(sourceProvider)
                .withTargetArtifactProvider(targetProvider);
    }

    private EvaluationConfiguration buildDefaults() {
        ModuleConfiguration sourcePreprocessor = new ModuleConfiguration("artifact", new HashMap<>());
        ModuleConfiguration targetPreprocessor = new ModuleConfiguration("artifact", new HashMap<>());

        //TODO probably move to params too
        ModuleConfiguration embeddingCreator = new ModuleConfiguration("openai", new HashMap<>(Map.of("model", "text-embedding-3-large")));

        ModuleConfiguration sourceStore = new ModuleConfiguration("custom", new HashMap<>());
        ModuleConfiguration targetStore = new ModuleConfiguration("cosine_similarity", new HashMap<>());

        //TODO remove default model, move to params
        ModuleConfiguration classifier = new ModuleConfiguration("simple_openai", new HashMap<>(Map.of("model", "gpt-4o-mini-2024-07-18")));

        ModuleConfiguration resultAggregator = new ModuleConfiguration("any_connection", new HashMap<>());
        ModuleConfiguration tracelinkIdPostprocessor = new ModuleConfiguration("identity", new HashMap<>());

        return new EvaluationConfiguration(null, null, null, null, sourcePreprocessor, targetPreprocessor, embeddingCreator, sourceStore, targetStore,
                classifier, null, resultAggregator, tracelinkIdPostprocessor);
    }

    /**
     * Applies caller-supplied overrides for the non-structural module fields only.
     * {@code cache_dir}/{@code gold_standard_configuration}/{@code source_artifact_provider}/
     * {@code target_artifact_provider} nodes, even if present in {@code additionalConfig}, are
     * deliberately never read here — see the caller for where those are enforced instead.
     */
    private EvaluationConfiguration applyOverrides(EvaluationConfiguration base, String additionalConfigJson) {

        EvaluationConfiguration additionalConfig;
        try {
            additionalConfig = mapper.readValue(additionalConfigJson, EvaluationConfiguration.class);
        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse additional LiSSA configuration JSON; using defaults. Error: {}", e.getMessage());
            return base;
        }

        EvaluationConfiguration result = base;
        result = result.withSourcePreprocessor(additionalConfig.sourcePreprocessor())
                .withTargetPreprocessor(additionalConfig.targetPreprocessor())
                .withEmbeddingCreator(additionalConfig.embeddingCreator())
                .withSourceStore(additionalConfig.sourceStore())
                .withTargetStore(additionalConfig.targetStore())
                .withResultAggregator(additionalConfig.resultAggregator())
                .withTraceLinkIdPostprocessor(additionalConfig.traceLinkIdPostprocessor());

        if (additionalConfig.classifiers() != null) {
            result = result.withClassifiers(additionalConfig.classifiers()).withClassifier(null);
        } else if (additionalConfig.classifier() != null) {
            result = result.withClassifier(additionalConfig.classifier()).withClassifiers(null);
        }

        return result;
    }

    private Path copyIntoDirectory(List<File> files, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            for (File file : files) {
                Files.copy(file.toPath(), targetDir.resolve(file.getName()), StandardCopyOption.REPLACE_EXISTING);
            }
            return targetDir;
        } catch (IOException e) {
            throw new ArdocoException("Failed to prepare LiSSA input directory " + targetDir + ": " + e.getMessage(), e);
        }
    }
}
