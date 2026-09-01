/* Licensed under MIT 2026. */
package edu.kit.kastel.mcse.ardoco.tlr.rest.api.api_response;

/** Mirrors LiSSA's edu.kit.kastel.sdq.lissa.ratlr.knowledge.TraceLink record for JSON serialization. */
public record LissaTraceLink(String sourceId, String targetId) {
}