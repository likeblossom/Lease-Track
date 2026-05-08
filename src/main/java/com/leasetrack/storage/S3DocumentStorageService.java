package com.leasetrack.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class S3DocumentStorageService implements DocumentStorageService {

    @Override
    public StoredDocument store(DocumentStorageRequest request) {
        throw new UnsupportedOperationException("S3 document storage is not implemented yet");
    }
}
