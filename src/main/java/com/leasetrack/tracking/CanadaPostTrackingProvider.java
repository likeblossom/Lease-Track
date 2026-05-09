package com.leasetrack.tracking;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
@ConditionalOnProperty(name = "app.tracking.canada-post.enabled", havingValue = "true")
public class CanadaPostTrackingProvider implements TrackingProvider, org.springframework.beans.factory.InitializingBean {

    private static final Pattern URL_NUMBER_PATTERN = Pattern.compile(
            "(?i)(?:pin|dnc|details|trackingNumber|tracking-number|track=|id=)[/=#?&:]*([A-Z0-9]{8,40})");
    private static final Pattern FALLBACK_NUMBER_PATTERN = Pattern.compile("(?i)([A-Z]{0,4}\\d[A-Z0-9]{7,35})");

    private final RestClient restClient;
    private final String username;
    private final String password;

    public CanadaPostTrackingProvider(
            RestClient.Builder restClientBuilder,
            @Value("${app.tracking.canada-post.base-url:https://ct.soa-gw.canadapost.ca}") String baseUrl,
            @Value("${app.tracking.canada-post.username:}") String username,
            @Value("${app.tracking.canada-post.password:}") String password,
            @Value("${app.tracking.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${app.tracking.read-timeout:PT15S}") Duration readTimeout) {
        RestClient.Builder builder = restClientBuilder.baseUrl(baseUrl);
        if (connectTimeout != null && readTimeout != null) {
            builder.requestFactory(requestFactory(connectTimeout, readTimeout));
        }
        this.restClient = builder.build();
        this.username = username;
        this.password = password;
    }

    @Override
    public String carrierCode() {
        return "canada-post";
    }

    @Override
    public void afterPropertiesSet() {
        requireConfigured();
    }

    @Override
    public Optional<String> parseTrackingNumber(String trackingUrl) {
        String lowerUrl = trackingUrl == null ? "" : trackingUrl.toLowerCase(Locale.ROOT);
        if (!lowerUrl.contains("canadapost") && !lowerUrl.contains("postescanada")) {
            return Optional.empty();
        }
        return extract(URL_NUMBER_PATTERN, trackingUrl).or(() -> extract(FALLBACK_NUMBER_PATTERN, trackingUrl));
    }

    @Override
    public TrackingSummary track(String trackingNumber) {
        requireConfigured();
        try {
            String response = restClient.get()
                    .uri("/vis/track/pin/{pin}/summary", trackingNumber)
                    .accept(MediaType.APPLICATION_XML)
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .retrieve()
                    .body(String.class);
            String details = fetchDetails(trackingNumber).orElse(null);
            return parseSummary(trackingNumber, response, details);
        } catch (RestClientResponseException ex) {
            throw TrackingProviderHttpErrors.classify("Canada Post", ex);
        } catch (ResourceAccessException ex) {
            throw TrackingProviderHttpErrors.timeout("Canada Post", ex);
        } catch (TrackingProviderException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.PERMANENT,
                    "Canada Post tracking lookup failed",
                    ex);
        }
    }

    private Optional<String> fetchDetails(String trackingNumber) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/vis/track/pin/{pin}/detail", trackingNumber)
                    .accept(MediaType.APPLICATION_XML)
                    .headers(headers -> headers.setBasicAuth(username, password))
                    .retrieve()
                    .body(String.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw ex;
        }
    }

    private TrackingSummary parseSummary(String trackingNumber, String xml, String detailsXml) {
        Document document = parseXml(xml);
        String statusCode = firstText(document, "event-type").orElse(null);
        String description = firstText(document, "event-description")
                .or(() -> firstText(document, "expected-delivery-date"))
                .orElse("Canada Post tracking status unavailable");
        Instant eventAt = firstText(document, "event-date-time")
                .or(() -> firstText(document, "event-date"))
                .flatMap(this::parseInstant)
                .orElse(null);
        boolean delivered = "DELIVERED".equalsIgnoreCase(statusCode)
                || description.toLowerCase(Locale.ROOT).contains("delivered");
        List<TrackingEvent> events = detailsXml == null || detailsXml.isBlank()
                ? List.of(new TrackingEvent(
                        TrackingSummary.eventKey(trackingNumber, statusCode, description, eventAt),
                        trackingNumber,
                        description,
                        statusCode,
                        delivered,
                        eventAt))
                : parseDetailEvents(trackingNumber, detailsXml);

        return new TrackingSummary(
                trackingNumber,
                description,
                statusCode,
                delivered,
                eventAt,
                detailsXml == null ? xml : detailsXml,
                events);
    }

    private List<TrackingEvent> parseDetailEvents(String trackingNumber, String xml) {
        Document document = parseXml(xml);
        NodeList eventNodes = document.getElementsByTagName("significant-event");
        if (eventNodes.getLength() == 0) {
            eventNodes = document.getElementsByTagName("tracking-event");
        }

        List<TrackingEvent> events = new ArrayList<>();
        for (int i = 0; i < eventNodes.getLength(); i++) {
            if (!(eventNodes.item(i) instanceof org.w3c.dom.Element element)) {
                continue;
            }
            String statusCode = firstText(element, "event-type").orElse(null);
            String description = firstText(element, "event-description")
                    .orElse("Canada Post tracking event");
            Instant eventAt = firstText(element, "event-date-time")
                    .or(() -> firstText(element, "event-date"))
                    .flatMap(this::parseInstant)
                    .orElse(null);
            boolean delivered = "DELIVERED".equalsIgnoreCase(statusCode)
                    || description.toLowerCase(Locale.ROOT).contains("delivered");
            events.add(new TrackingEvent(
                    TrackingSummary.eventKey(trackingNumber, statusCode, description, eventAt),
                    trackingNumber,
                    description,
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

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            secure(factory);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception ex) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.PARSE_ERROR,
                    "Unable to parse Canada Post tracking response",
                    ex);
        }
    }

    private void secure(DocumentBuilderFactory factory) throws ParserConfigurationException {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }

    private Optional<String> firstText(Document document, String tagName) {
        NodeList nodes = document.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return Optional.empty();
        }
        String text = nodes.item(0).getTextContent().trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private Optional<String> firstText(org.w3c.dom.Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return Optional.empty();
        }
        String text = nodes.item(0).getTextContent().trim();
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private Optional<Instant> parseInstant(String value) {
        try {
            if (value.length() == 10) {
                return Optional.of(Instant.parse(value + "T00:00:00Z"));
            }
            return Optional.of(Instant.parse(value));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> extract(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).toUpperCase(Locale.ROOT));
    }

    private void requireConfigured() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new TrackingProviderException(
                    TrackingProviderErrorType.CONFIGURATION,
                    "Canada Post tracking credentials are not configured");
        }
    }

    private SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}
