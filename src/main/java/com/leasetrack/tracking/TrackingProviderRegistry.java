package com.leasetrack.tracking;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TrackingProviderRegistry {

    private final List<TrackingProvider> providers;

    public TrackingProviderRegistry(List<TrackingProvider> providers) {
        this.providers = providers;
    }

    public Optional<TrackingProvider> findByCarrier(String carrier) {
        String normalized = normalizeCarrierCode(carrier);
        if (normalized == null) {
            return Optional.empty();
        }
        return providers.stream()
                .filter(provider -> provider.supportsCarrier(normalized))
                .findFirst();
    }

    public Optional<TrackingInput> resolve(String carrier, String trackingNumber, String trackingUrl) {
        String normalizedTrackingNumber = blankToNull(trackingNumber);
        String normalizedCarrier = normalizeCarrierCode(carrier);

        if (normalizedTrackingNumber != null && normalizedCarrier != null) {
            return Optional.of(new TrackingInput(normalizedCarrier, normalizedTrackingNumber));
        }

        String normalizedUrl = blankToNull(trackingUrl);
        if (normalizedUrl == null) {
            return Optional.empty();
        }

        return providers.stream()
                .flatMap(provider -> provider.parseTrackingNumber(normalizedUrl)
                        .map(number -> new TrackingInput(provider.carrierCode(), number))
                        .stream())
                .findFirst();
    }

    public static String normalizeCarrierCode(String carrier) {
        String value = blankToNull(carrier);
        if (value == null) {
            return null;
        }

        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("_", "-")
                .replace(" ", "-")
                .replace(".", "")
                .trim();

        return switch (normalized) {
            case "canada-post", "canadapost", "postes-canada", "postescanada", "cpc" -> "canada-post";
            case "fedex", "fed-ex", "federal-express" -> "fedex";
            case "mock" -> "mock";
            default -> normalized;
        };
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
