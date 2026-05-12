package com.leasetrack.scheduler;

import com.leasetrack.service.TrackingSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrackingSyncScheduler {

    private final TrackingSyncService trackingSyncService;

    public TrackingSyncScheduler(TrackingSyncService trackingSyncService) {
        this.trackingSyncService = trackingSyncService;
    }

    @Scheduled(fixedDelayString = "${app.schedulers.tracking.fixed-delay-ms}")
    public void syncTracking() {
        trackingSyncService.enqueueRegisteredMailTracking();
    }
}
