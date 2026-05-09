package com.leasetrack.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.twice;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class FedExTrackingProviderTest {

    @Test
    void parsesTrackingNumberFromTrackingUrl() {
        FedExTrackingProvider provider = new FedExTrackingProvider(
                RestClient.builder(),
                new ObjectMapper(),
                "https://auth.example.test",
                "https://track.example.test",
                "client",
                "secret",
                null,
                null);

        assertThat(provider.parseTrackingNumber("https://www.fedex.com/fedextrack/?trknbr=781234567890"))
                .contains("781234567890");
    }

    @Test
    void reusesOauthTokenAndUsesNewestScanEvent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FedExTrackingProvider provider = new FedExTrackingProvider(
                builder,
                new ObjectMapper(),
                "https://apis-sandbox.fedex.com/oauth/token",
                "https://apis-sandbox.fedex.com/track/v1/trackingnumbers",
                "client",
                "secret",
                null,
                null);
        String trackingResponse = """
                {
                  "output": {
                    "completeTrackResults": [{
                      "trackResults": [{
                        "latestStatusDetail": {
                          "code": "IT",
                          "description": "In transit"
                        },
                        "scanEvents": [
                          {"date": "2026-05-08T10:00:00Z", "eventType": "PU", "eventDescription": "Picked up"},
                          {"date": "2026-05-08T14:30:00Z", "eventType": "IT", "eventDescription": "In transit"},
                          {"date": "2026-05-07T18:00:00Z", "eventType": "OC", "eventDescription": "Shipment information sent"}
                        ]
                      }]
                    }]
                  }
                }
                """;

        server.expect(once(), requestTo("https://apis-sandbox.fedex.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        server.expect(twice(), requestTo("https://apis-sandbox.fedex.com/track/v1/trackingnumbers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
                .andRespond(withSuccess(trackingResponse, MediaType.APPLICATION_JSON));

        TrackingSummary first = provider.track("781234567890");
        TrackingSummary second = provider.track("781234567890");

        assertThat(first.status()).isEqualTo("In transit");
        assertThat(first.statusCode()).isEqualTo("IT");
        assertThat(first.eventAt()).isEqualTo(Instant.parse("2026-05-08T14:30:00Z"));
        assertThat(first.events()).hasSize(3);
        assertThat(first.events().getLast().eventAt()).isEqualTo(Instant.parse("2026-05-08T14:30:00Z"));
        assertThat(second.eventAt()).isEqualTo(first.eventAt());
        server.verify();
    }
}
