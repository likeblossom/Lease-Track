package com.leasetrack.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CanadaPostTrackingProviderTest {

    @Test
    void parsesTrackingNumberFromTrackingUrl() {
        CanadaPostTrackingProvider provider = new CanadaPostTrackingProvider(
                RestClient.builder(),
                "https://example.test",
                "user",
                "pass",
                null,
                null);

        assertThat(provider.parseTrackingNumber(
                "https://www.canadapost-postescanada.ca/track-reperage/en#/details/RN123456789CA"))
                .contains("RN123456789CA");
    }

    @Test
    void mapsSummaryResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CanadaPostTrackingProvider provider = new CanadaPostTrackingProvider(
                builder,
                "https://ct.soa-gw.canadapost.ca",
                "user",
                "pass",
                null,
                null);
        String xml = """
                <tracking-summary>
                  <event-type>DELIVERED</event-type>
                  <event-description>Delivered</event-description>
                  <event-date>2026-05-08</event-date>
                </tracking-summary>
                """;
        String detailsXml = """
                <tracking-detail>
                  <significant-event>
                    <event-type>IN_TRANSIT</event-type>
                    <event-description>In transit</event-description>
                    <event-date>2026-05-07</event-date>
                  </significant-event>
                  <significant-event>
                    <event-type>DELIVERED</event-type>
                    <event-description>Delivered</event-description>
                    <event-date>2026-05-08</event-date>
                  </significant-event>
                </tracking-detail>
                """;

        server.expect(requestTo("https://ct.soa-gw.canadapost.ca/vis/track/pin/RN123456789CA/summary"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));
        server.expect(requestTo("https://ct.soa-gw.canadapost.ca/vis/track/pin/RN123456789CA/detail"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(detailsXml, MediaType.APPLICATION_XML));

        TrackingSummary summary = provider.track("RN123456789CA");

        assertThat(summary.status()).isEqualTo("Delivered");
        assertThat(summary.statusCode()).isEqualTo("DELIVERED");
        assertThat(summary.delivered()).isTrue();
        assertThat(summary.eventAt()).isEqualTo(Instant.parse("2026-05-08T00:00:00Z"));
        assertThat(summary.rawProviderPayload()).isEqualTo(detailsXml);
        assertThat(summary.events()).hasSize(2);
        assertThat(summary.events().getLast().statusCode()).isEqualTo("DELIVERED");
        server.verify();
    }

    @Test
    void fetchesDeliveryConfirmationCertificate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CanadaPostTrackingProvider provider = new CanadaPostTrackingProvider(
                builder,
                "https://ct.soa-gw.canadapost.ca",
                "user",
                "pass",
                null,
                null);
        byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.UTF_8);
        String xml = """
                <delivery-confirmation-certificate>
                  <filename>RN123456789CA.pdf</filename>
                  <image>%s</image>
                  <mime-type>application/pdf</mime-type>
                </delivery-confirmation-certificate>
                """.formatted(Base64.getEncoder().encodeToString(pdf));

        server.expect(requestTo("https://ct.soa-gw.canadapost.ca/vis/certificate/RN123456789CA"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("user:pass".getBytes(StandardCharsets.UTF_8))))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));

        Optional<DeliveryConfirmationCertificate> certificate =
                provider.fetchDeliveryConfirmationCertificate("RN123456789CA");

        assertThat(certificate).isPresent();
        assertThat(certificate.get().filename()).isEqualTo("RN123456789CA.pdf");
        assertThat(certificate.get().contentType()).isEqualTo("application/pdf");
        assertThat(certificate.get().content()).isEqualTo(pdf);
        server.verify();
    }
}
