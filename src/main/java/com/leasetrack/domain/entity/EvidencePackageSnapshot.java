package com.leasetrack.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evidence_package_snapshots")
public class EvidencePackageSnapshot {

    @Id
    private UUID id;

    @Column(name = "notice_id", nullable = false)
    private UUID noticeId;

    @Column(name = "package_version", nullable = false)
    private String packageVersion;

    @Column(name = "package_hash", nullable = false)
    private String packageHash;

    @Column(name = "generated_by_user_id", nullable = false)
    private UUID generatedByUserId;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "package_json", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String packageJson;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getNoticeId() {
        return noticeId;
    }

    public void setNoticeId(UUID noticeId) {
        this.noticeId = noticeId;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public String getPackageHash() {
        return packageHash;
    }

    public void setPackageHash(String packageHash) {
        this.packageHash = packageHash;
    }

    public UUID getGeneratedByUserId() {
        return generatedByUserId;
    }

    public void setGeneratedByUserId(UUID generatedByUserId) {
        this.generatedByUserId = generatedByUserId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getPackageJson() {
        return packageJson;
    }

    public void setPackageJson(String packageJson) {
        this.packageJson = packageJson;
    }
}
