/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.rest.api.lissa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import edu.kit.kastel.sdq.lissa.ratlr.configuration.EvaluationConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.ModuleConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact.ArtifactType;

import edu.kit.kastel.mcse.ardoco.tlr.rest.api.exception.ArdocoException;

import java.io.File;

@Component
public class LissaEvaluationConfigFactory {

    /**
     * Builds a LiSSA evaluation configuration based on the provided input files and artifact types.
     *
     * @param sourceFiles   the source files to include in the evaluation
     * @param sourceType    the type of the source artifacts
     * @param targetFiles   the target files to include in the evaluation
     * @param targetType    the type of the target artifacts
     * @param workingDir    the working directory for the evaluation
     * @return              the built evaluation configuration
     */
    public EvaluationConfiguration build(List<File> sourceFiles, ArtifactType sourceType, List<File> targetFiles, ArtifactType targetType,
                                         Path workingDir) {
        Path sourceDir = copyIntoDirectory(sourceFiles, workingDir.resolve("source"));
        Path targetDir = copyIntoDirectory(targetFiles, workingDir.resolve("target"));

        ModuleConfiguration sourceProvider = new ModuleConfiguration("text", Map.of("path", sourceDir.toString(), "artifact_type", sourceType.name()));
        ModuleConfiguration targetProvider = new ModuleConfiguration("text", Map.of("path", targetDir.toString(), "artifact_type", targetType.name()));

        // TODO: adjust to real registered module names/args (see Preprocessor, EmbeddingCreator,
        // ResultAggregator factories in LiSSA for valid options).
        ModuleConfiguration sourcePreprocessor = new ModuleConfiguration("sentence", Map.of());
        ModuleConfiguration targetPreprocessor = new ModuleConfiguration("sentence", Map.of());
        ModuleConfiguration embeddingCreator = new ModuleConfiguration("openai", Map.of("model", "text-embedding-3-large"));
        ModuleConfiguration sourceStore = new ModuleConfiguration("default", Map.of());
        ModuleConfiguration targetStore = new ModuleConfiguration("default", Map.of());
        ModuleConfiguration classifier = new ModuleConfiguration("llm", Map.of("model", "gpt-4o-mini"));
        ModuleConfiguration resultAggregator = new ModuleConfiguration("any_connection", Map.of());

        return new EvaluationConfiguration(
                workingDir.resolve("cache").toString(),
                null,               // goldStandardConfiguration
                sourceProvider,
                targetProvider,
                sourcePreprocessor,
                targetPreprocessor,
                embeddingCreator,
                sourceStore,
                targetStore,
                classifier,
                null,               // classifiers (multi-stage) - mutually exclusive with `classifier`
                resultAggregator,
                null                // traceLinkIdPostprocessor
        );
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