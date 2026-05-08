package com.leasetrack.scheduler;

import com.leasetrack.service.DeadlineReminderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeadlineReminderScheduler {

    private final DeadlineReminderService deadlineReminderService;

    public DeadlineReminderScheduler(DeadlineReminderService deadlineReminderService) {
        this.deadlineReminderService = deadlineReminderService;
    }

    @Scheduled(fixedDelayString = "${app.schedulers.deadline.fixed-delay-ms}")
    public void publishDeadlineReminders() {
        deadlineReminderService.publishDueReminders();
    }
}
