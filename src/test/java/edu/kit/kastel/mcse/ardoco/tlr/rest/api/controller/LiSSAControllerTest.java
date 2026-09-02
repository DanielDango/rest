/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.rest.api.controller;

import static edu.kit.kastel.mcse.ardoco.tlr.rest.api.controller.LissaController.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Testcontainers;

import edu.kit.kastel.mcse.ardoco.tlr.rest.ArDoCoRestApplication;
import edu.kit.kastel.mcse.ardoco.tlr.rest.api.api_response.TraceLinkType;
import edu.kit.kastel.sdq.lissa.ratlr.knowledge.Artifact.ArtifactType;

@Testcontainers
@SpringBootTest(classes = ArDoCoRestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LiSSAControllerTest extends AbstractTLRControllerTest {

    private static final String[] HIGH_LEVEL_REQUIREMENTS = { "dronology/requirements/high/RE-8.txt", "dronology/requirements/high/RE-9.txt",
            "dronology/requirements/high/RE-25.txt" };
    private static final String[] LOW_LEVEL_REQUIREMENTS = { "dronology/requirements/low/DD-11.txt", "dronology/requirements/low/DD-18.txt",
            "dronology/requirements/low/DD-19.txt" };

    public LiSSAControllerTest() {
        super(TraceLinkType.LISSA);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void testRunPipelineAndGetResult_dronology() throws IOException {
        HttpEntity<MultiValueMap<String, Object>> requestEntity = setUpRequestParamToStartPipeline("dronology");
        runPipeline_start_and_getResult(requestEntity);
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    void testRunPipelineAndWaitForResult_dronology() throws IOException {
        HttpEntity<MultiValueMap<String, Object>> requestEntity = setUpRequestParamToStartPipeline("dronology2");
        test_runPipelineAndWaitForResult_helper(requestEntity);
    }

    @Override
    protected HttpEntity<MultiValueMap<String, Object>> prepareRequestEntityForEmptyFileTest(String projectName) {
        LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
        parameters.add(PROJECT_NAME_PARAMETER, projectName);
        parameters.add(SOURCE_ARTIFACTS_PARAMETER, new ClassPathResource("emptyFile.txt"));
        parameters.add(SOURCE_ARTIFACT_TYPE_PARAMETER, ArtifactType.REQUIREMENT.toString());
        parameters.add(TARGET_ARTIFACTS_PARAMETER, new ClassPathResource("emptyFile.txt"));
        parameters.add(TARGET_ARTIFACT_TYPE_PARAMETER, ArtifactType.REQUIREMENT.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parameters, headers);
    }

    private HttpEntity<MultiValueMap<String, Object>> setUpRequestParamToStartPipeline(String projectName) {
        LinkedMultiValueMap<String, Object> parameters = new LinkedMultiValueMap<>();
        parameters.add(PROJECT_NAME_PARAMETER, projectName);

        for (String path : HIGH_LEVEL_REQUIREMENTS) {
            parameters.add(SOURCE_ARTIFACTS_PARAMETER, new ClassPathResource(path));
        }
        parameters.add(SOURCE_ARTIFACT_TYPE_PARAMETER, ArtifactType.REQUIREMENT.toString());

        for (String path : LOW_LEVEL_REQUIREMENTS) {
            parameters.add(TARGET_ARTIFACTS_PARAMETER, new ClassPathResource(path));
        }
        parameters.add(TARGET_ARTIFACT_TYPE_PARAMETER, ArtifactType.REQUIREMENT.toString());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(parameters, headers);
    }
}
