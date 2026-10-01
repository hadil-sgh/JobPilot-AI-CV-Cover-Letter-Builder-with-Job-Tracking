package com.jobpilot.template;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.jobpilot.common.error.ApiException;

/**
 * Client for the sandboxed latex-worker (POST /compile). The backend never runs LaTeX itself;
 * compile errors are logged with the LaTeX log tail and reported to the user in plain words.
 */
@Component
public class LatexWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(LatexWorkerClient.class);

    private final RestClient http;
    private final ObjectMapper json;

    public LatexWorkerClient(@Value("${jobpilot.latex-worker-url}") String baseUrl, ObjectMapper json) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(45)); // worker: 20 s compile + queueing
        // Buffering sends a Content-Length header: the worker's stdlib HTTP server does not read chunked bodies.
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(new BufferingClientHttpRequestFactory(factory)).build();
        this.json = json;
    }

    /** Compiles {@code main} (one of {@code files}) and returns the PDF bytes. */
    public byte[] compile(String main, Map<String, String> files) {
        try {
            byte[] pdf = http.post().uri("/compile")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
                    .body(Map.of("main", main, "files", files))
                    .retrieve()
                    .body(byte[].class);
            if (pdf == null || pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P') {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "The PDF service returned an invalid file.");
            }
            return pdf;
        } catch (HttpClientErrorException e) {
            log.warn("LaTeX compilation of {} failed ({}): {}", main, e.getStatusCode(), logTail(e));
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "The PDF could not be generated with this template. Try another template or option.");
        } catch (RestClientResponseException e) {
            log.error("latex-worker error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The PDF service is busy or failing. Please try again.");
        } catch (ResourceAccessException e) {
            log.error("latex-worker unreachable", e);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "The PDF service is not reachable. Is the latex-worker running?");
        }
    }

    private String logTail(RestClientResponseException e) {
        try {
            JsonNode body = json.readTree(e.getResponseBodyAsString());
            return body.path("error").asText() + "\n" + body.path("log").asText();
        } catch (Exception parse) {
            return e.getResponseBodyAsString();
        }
    }
}
