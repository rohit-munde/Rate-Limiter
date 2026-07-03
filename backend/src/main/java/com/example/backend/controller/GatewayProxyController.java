package com.example.backend.controller;

import com.example.backend.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
public class GatewayProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final GatewayProperties gatewayProperties;
    private final HttpClient httpClient;

    public GatewayProxyController(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @RequestMapping("/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest servletRequest, @RequestBody(required = false) byte[] body) {
        URI targetUri = buildTargetUri(servletRequest);
        HttpRequest proxyRequest = buildProxyRequest(servletRequest, targetUri, body);

        try {
            HttpResponse<byte[]> proxyResponse = httpClient.send(proxyRequest, HttpResponse.BodyHandlers.ofByteArray());
            return ResponseEntity
                    .status(proxyResponse.statusCode())
                    .headers(toResponseHeaders(proxyResponse.headers().map()))
                    .body(proxyResponse.body());
        } catch (IOException e) {
            return badGateway("Downstream service is not reachable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return badGateway("Gateway request was interrupted.");
        }
    }

    private URI buildTargetUri(HttpServletRequest request) {
        String baseUrl = gatewayProperties.getTargetBaseUrl().replaceAll("/+$", "");
        String query = request.getQueryString() == null ? "" : "?" + request.getQueryString();
        return URI.create(baseUrl + request.getRequestURI() + query);
    }

    private HttpRequest buildProxyRequest(HttpServletRequest servletRequest, URI targetUri, byte[] body) {
        HttpRequest.BodyPublisher bodyPublisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri)
                .method(servletRequest.getMethod(), bodyPublisher);

        Collections.list(servletRequest.getHeaderNames()).forEach(headerName -> {
            if (shouldForwardHeader(headerName)) {
                Collections.list(servletRequest.getHeaders(headerName))
                        .forEach(headerValue -> builder.header(headerName, headerValue));
            }
        });

        String clientIp = servletRequest.getRemoteAddr();
        String existingForwardedFor = servletRequest.getHeader("X-Forwarded-For");
        builder.header(
                "X-Forwarded-For",
                existingForwardedFor == null ? clientIp : existingForwardedFor + ", " + clientIp
        );
        builder.header("X-Forwarded-Proto", servletRequest.getScheme());
        builder.header("X-Forwarded-Host", servletRequest.getHeader("Host"));

        return builder.build();
    }

    private HttpHeaders toResponseHeaders(java.util.Map<String, List<String>> responseHeaders) {
        HttpHeaders headers = new HttpHeaders();
        Set<String> copiedHeaders = new HashSet<>();

        responseHeaders.forEach((headerName, values) -> {
            if (shouldForwardHeader(headerName) && copiedHeaders.add(headerName.toLowerCase())) {
                values.forEach(value -> headers.add(headerName, value));
            }
        });

        return headers;
    }

    private boolean shouldForwardHeader(String headerName) {
        return headerName != null && !HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
    }

    private ResponseEntity<byte[]> badGateway(String message) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(("{\"message\":\"" + escapeJson(message) + "\"}").getBytes());
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
