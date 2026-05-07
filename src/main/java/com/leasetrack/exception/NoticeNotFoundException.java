package com.leasetrack.exception;

import java.util.UUID;

public class NoticeNotFoundException extends RuntimeException {

    public NoticeNotFoundException(UUID noticeId) {
        super("Notice not found: " + noticeId);
    }
}
