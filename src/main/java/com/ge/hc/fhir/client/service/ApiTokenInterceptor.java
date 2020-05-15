package com.ge.hc.fhir.client.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.client.api.IClientInterceptor;
import ca.uhn.fhir.rest.client.api.IHttpRequest;
import ca.uhn.fhir.rest.client.api.IHttpResponse;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;

@Component
public class ApiTokenInterceptor implements IClientInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiTokenInterceptor.class);
    
    private String fhirApiKey;
    private Map<String, String> cookies = new HashMap<>();

    public ApiTokenInterceptor(@Value("${fhir.api-key}") String fhirfhirApiKey,
                               @Value("${proxy.url:#{null}}") String proxyUrl,
                               @Value("${proxy.port:0}") int proxyPort) {
        this.fhirApiKey = fhirfhirApiKey;
    }
    
    @Override
    public void interceptRequest(IHttpRequest request) {
        LOGGER.info(request.getUri().toString());
        request.addHeader("X-API-KEY", fhirApiKey);
        if (!cookies.isEmpty()) {
            StringBuilder cookieBuilder = new StringBuilder();
            for (Entry<String, String> cookieEntry : cookies.entrySet()) {
                cookieBuilder.append(cookieEntry.getKey()).append('=').append(cookieEntry.getValue()).append(';');
            }
            request.addHeader("Cookie", cookieBuilder.toString());
        }
    }

    @Override
    public void interceptResponse(IHttpResponse response) {
        try {
            response.bufferEntity();
            Map<String, List<String>> headers = response.getAllHeaders();
            List<String> cookiesList = headers.get("set-cookie");
            if (cookiesList != null) {
                for (String cookie: cookiesList) {
                    String[] cookieContent = cookie.split(";")[0].split("=");
                    cookies.put(cookieContent[0], cookieContent[1]);
                }
            }
            try (InputStream respEntity = response.readEntity()) {
                if (respEntity != null) {
                    final byte[] bytes;
                    try {
                        bytes = IOUtils.toByteArray(respEntity);
                    } catch (IllegalStateException e) {
                        throw new InternalErrorException(e);
                    }
                    LOGGER.info("{}\n", new String(bytes, StandardCharsets.UTF_8));
                } else {
                    LOGGER.info("Client response body: (none)");
                }
            }
        }
        catch (IOException e) {
            LOGGER.info("Error when buffering entity:", e);
        }
    }
}
