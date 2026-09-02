/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.rest.api.service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import edu.kit.kastel.sdq.lissa.ratlr.Evaluation;
import edu.kit.kastel.sdq.lissa.ratlr.configuration.EvaluationConfiguration;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact.ArtifactType;

import edu.kit.kastel.mcse.ardoco.tlr.rest.api.api_response.ArDoCoApiResult;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.api_response.LissaTraceLink;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.api_response.TraceLinkType;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.exception.ArdocoException;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.lissa.LissaEvaluationConfigFactory;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.repository.CurrentlyRunningRequestsRepository;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.repository.DatabaseAccessor;

@Service("LissaService")
public class LissaService extends AbstractService {

    private static final Logger logger = LoggerFactory.getLogger(LissaService.class);
    private static final String ERROR_PREFIX = "Error: ";

    @Autowired
    private CurrentlyRunningRequestsRepository currentlyRunningRequestsRepository;

    @Autowired
    private DatabaseAccessor databaseAccessor;

    @Autowired
    private LissaEvaluationConfigFactory configFactory;

    private final ObjectMapper mapper = new ObjectMapper();

    public Optional<ArDoCoApiResult> runPipeline(List<File> sourceFiles, ArtifactType sourceType, List<File> targetFiles, ArtifactType targetType,
                                                 String id) {
        if (!resultIsInDatabase(id) && !resultIsOnItsWay(id)) {
            logger.info("Start new TLR of type {} for {}", TraceLinkType.LISSA, id);
            CompletableFuture<ArDoCoApiResult> future =
                    CompletableFuture.supplyAsync(() -> runPipelineAsync(sourceFiles, sourceType, targetFiles, targetType, id));
            currentlyRunningRequestsRepository.addRequest(id, future);
        } else if (resultIsInDatabase(id)) {
            return Optional.of(getResultFromDatabase(id));
        }
        return Optional.empty();
    }

    private ArDoCoApiResult runPipelineAsync(List<File> sourceFiles, ArtifactType sourceType, List<File> targetFiles, ArtifactType targetType,
                                             String id) {
        ArDoCoApiResult traceLinkJson;
        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("lissa-" + id);
            EvaluationConfiguration config = configFactory.build(sourceFiles, sourceType, targetFiles, targetType, workingDir);

            logger.info("Using the following config: {}", config);

            logger.info("Running LiSSA evaluation in-process...");
            Evaluation evaluation = new Evaluation(config);
            Set<TraceLink> traceLinks = evaluation.run();

            List<LissaTraceLink> mapped = traceLinks.stream()
                    .map(tl -> new LissaTraceLink(tl.sourceId(), tl.targetId()))
                    .toList();
            traceLinkJson = new ArDoCoApiResult(mapper.writeValueAsString(mapped));
            databaseAccessor.saveResult(id, traceLinkJson.buildJsonString());

        } catch (Exception e) {
            String message = String.format("Error occurred while running LiSSA asynchronously for ID %s : %s", id, e.getMessage());
            logger.error(message, e);
            databaseAccessor.saveResult(id, ERROR_PREFIX + message);
            throw new ArdocoException(message, e);
        } finally {
            currentlyRunningRequestsRepository.removeRequest(id);
            for (File file : sourceFiles) file.delete();
            for (File file : targetFiles) file.delete();
            deleteQuietly(workingDir);
        }
        return traceLinkJson;
    }

    private void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        } catch (Exception e) {
            logger.warn("Failed to clean up LiSSA working directory {}: {}", dir, e.getMessage());
        }
    }
}