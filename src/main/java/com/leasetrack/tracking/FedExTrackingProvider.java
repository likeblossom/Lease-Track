package com.leasetrack.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
@ConditionalOnProperty(name = "app.tracking.fedex.enabled", havingValue = "true")
public class FedExTrackingProvider implements TrackingProvider, org.springframework.beans.factory.InitializingBean {

    private static final Pattern URL_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:trknbr|tracknumbers|trackingnumber|tracking-number|track=|id=)[/=#?&:]*([0-9]{8,40})");
    private static final Pattern FALLBACK_NUMBER_PATTERN = Pattern.compile("(?i)([0-9]{12,40})");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authUrl;
    private final String trackUrl;
    private final String clientId;
    private final String clientSecret;
    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public FedExTrackingProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.tracking.fedex.auth-url:https://apis-sandbox.fedex.com/oauth/token}") String authUrl,
            @Value("${app.tracking.fedex.track-url:https://apis-sandbox.fedex.com/track/v1/trackingnumbers}") String trackUrl,
            @Value("${app.tracking.fedex.client-id:}") String clientId,
            @Value("${app.tracking.fedex.client-secret:}") String clientSecret,
            @Value("${app.tracking.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.tracking.read-timeout:PT15S}") Duration readTimeout) {
        if (connectTimeout != null && readTimeout != null) {
            restClientBuilder.requestFactory(requestFactory(connectTimeout, readTimeout));
        }
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.authUrl = authUrl;
        this.trackUrl = trackUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String carrierCode() {
        return "fedex";
    }

    @Override
    public void afterPropertiesSet() {
        requireConfigured();
    }

    @Override
    public Optional<String> parseTrackingNumber(String trackingUrl) {
        if (trackingUrl == null || !trackingUrl.toLowerCase(Locale.ROOT).contains("fedex")) {
            return Optional.empty();
        }
        return extract(URL_NUMBER_PATTERN, trackingUrl).or(() -> extract(FALLBACK_NUMBER_PATTERN, trackingUrl));
    }

    @Override
    public TrackingSummary track(String trackingNumber) {
        requireConfigured();
        try {
            String token = getAccessToken();
            Map<String, Object> body = Map.of(
                    "includeDetailedScans", true,
                    "trackingInfo", new Object[] {
                        Map.of("trackingNumberInfo", Map.of("trackingNumber", trackingNumber))
                    });

            String response = restClient.post()
                    .uri(URI.create(trackUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseSummary(trackingNumber, response);
        } catch (RestClientResponseException ex) {
            throw TrackingProviderHttpErrors.classify("FedEx", ex);
        } catch (ResourceAccessException ex) {
            throw TrackingProviderHttpErrors.timeout("FedEx", ex);
        } catch (TrackingProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.PERMANENT,
                    "FedEx tracking lookup failed",
                    ex);
        }
    }

    private synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        JsonNode response = restClient.post()
                .uri(URI.create(authUrl))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (response == null || response.path("access_token").asText().isBlank()) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.AUTHENTICATION,
                    "FedEx OAuth response did not include an access token");
        }
        long expiresIn = response.path("expires_in").asLong(3600);
        cachedAccessToken = response.path("access_token").asText();
        tokenExpiresAt = now.plusSeconds(Math.max(60, expiresIn));
        return cachedAccessToken;
    }

    private TrackingSummary parseSummary(String trackingNumber, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode packageNode = root.path("output").path("completeTrackResults").path(0)
                    .path("trackResults").path(0);
            JsonNode statusNode = packageNode.path("latestStatusDetail");
            String statusCode = text(statusNode.path("code")).orElse(null);
            String description = text(statusNode.path("description"))
                    .or(() -> text(statusNode.path("scanLocation").path("city")))
                    .orElse("FedEx tracking status unavailable");
            List<TrackingEvent> events = scanEvents(trackingNumber, packageNode);
            Instant eventAt = events.stream()
                    .map(TrackingEvent::eventAt)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            boolean delivered = "DL".equalsIgnoreCase(statusCode)
                    || description.toLowerCase(Locale.ROOT).contains("delivered");

            if (events.isEmpty()) {
                events = List.of(new TrackingEvent(
                        TrackingSummary.eventKey(trackingNumber, statusCode, description, eventAt),
                        trackingNumber,
                        description,
                        statusCode,
                        delivered,
                        eventAt));
            }

            return new TrackingSummary(trackingNumber, description, statusCode, delivered, eventAt, json, events);
        } catch (Exception ex) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.PARSE_ERROR,
                    "Unable to parse FedEx tracking response",
                    ex);
        }
    }

    private Optional<String> text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(node.asText());
    }

    private Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private List<TrackingEvent> scanEvents(String trackingNumber, JsonNode packageNode) {
        JsonNode scanEvents = packageNode.path("scanEvents");
        if (!scanEvents.isArray()) {
            return List.of();
        }

        List<TrackingEvent> events = new ArrayList<>();
        for (JsonNode scanEvent : scanEvents) {
            Instant eventAt = text(scanEvent.path("date")).flatMap(this::parseInstant).orElse(null);
            String statusCode = text(scanEvent.path("eventType")).orElse(null);
            String status = text(scanEvent.path("eventDescription"))
                    .or(() -> text(scanEvent.path("derivedStatus")))
                    .orElse("FedEx tracking event");
            boolean delivered = "DL".equalsIgnoreCase(statusCode)
                    || status.toLowerCase(Locale.ROOT).contains("delivered");
            events.add(new TrackingEvent(
                    TrackingSummary.eventKey(trackingNumber, statusCode, status, eventAt),
                    trackingNumber,
                    status,
                    statusCode,
                    delivered,
                    eventAt));
        }
        return events.stream()
                .sorted(Comparator.comparing(
                        TrackingEvent::eventAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private Optional<String> extract(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private void requireConfigured() {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.CONFIGURATION,
                    "FedEx tracking credentials are not configured");
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
