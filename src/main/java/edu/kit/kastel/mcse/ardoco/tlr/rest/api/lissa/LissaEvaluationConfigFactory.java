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

import org.springframework.stereotype.Component;

import edu.kit.kastel.mcse.ardoco.tlr.rest.api.exception.ArdocoException;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.EvaluationConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.GoldStandardConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact.ArtifactType;

@Component
public class LissaEvaluationConfigFactory {

    /**
     * Builds a LiSSA evaluation configuration based on the provided input files and artifact types.
     *
     * @param sourceFiles the source files to include in the evaluation
     * @param sourceType  the type of the source artifacts
     * @param targetFiles the target files to include in the evaluation
     * @param targetType  the type of the target artifacts
     * @param workingDir  the working directory for the evaluation
     * @return the built evaluation configuration
     */
    public EvaluationConfiguration build(List<File> sourceFiles, ArtifactType sourceType, List<File> targetFiles, ArtifactType targetType, Path workingDir) {
        Path sourceDir = copyIntoDirectory(sourceFiles, workingDir.resolve("source"));
        Path targetDir = copyIntoDirectory(targetFiles, workingDir.resolve("target"));

        ModuleConfiguration sourceProvider = new ModuleConfiguration("text", new HashMap<>(Map.of("path", sourceDir.toString(), "artifact_type", sourceType
                .name())));

        ModuleConfiguration targetProvider = new ModuleConfiguration("text", new HashMap<>(Map.of("path", targetDir.toString(), "artifact_type", targetType
                .name())));

        ModuleConfiguration sourcePreprocessor = new ModuleConfiguration("artifact", new HashMap<>());
        ModuleConfiguration targetPreprocessor = new ModuleConfiguration("artifact", new HashMap<>());

        //TODO probably move to params too
        ModuleConfiguration embeddingCreator = new ModuleConfiguration("openai", new HashMap<>(Map.of("model", "text-embedding-3-large")));

        ModuleConfiguration sourceStore = new ModuleConfiguration("custom", new HashMap<>());
        ModuleConfiguration targetStore = new ModuleConfiguration("cosine_similarity", new HashMap<>());

        //TODO remove default model, move to params
        ModuleConfiguration classifier = new ModuleConfiguration("simple_openai", new HashMap<>(Map.of("model", "gpt-4o-mini-2024-07-18")));

        ModuleConfiguration resultAggregator = new ModuleConfiguration("any_connection", new HashMap<>());
        ModuleConfiguration tracelinkidPostprocessor = new ModuleConfiguration("identity", new HashMap<>());

        return new EvaluationConfiguration(workingDir.resolve("cache").toString(), new GoldStandardConfiguration(
                "src/main/java/edu/kit/kastel/mcse/ardoco/tlr/rest/api/lissa/emptyFile" + ".csv", false, false), sourceProvider, targetProvider,
                sourcePreprocessor, targetPreprocessor, embeddingCreator, sourceStore, targetStore, classifier, null,               // classifiers (multi-stage) - mutually exclusive with `classifier`
                resultAggregator, tracelinkidPostprocessor);
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
