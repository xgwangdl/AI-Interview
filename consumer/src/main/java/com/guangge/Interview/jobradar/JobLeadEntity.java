package com.guangge.Interview.jobradar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_leads")
public class JobLeadEntity {
    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 80)
    private String ownerId;
    @Column(length = 120)
    private String company;
    @Column(nullable = false, length = 120)
    private String role;
    @Column(length = 60)
    private String city;
    @Column(name = "salary_min_k")
    private Integer salaryMinK;
    @Column(name = "salary_max_k")
    private Integer salaryMaxK;
    @Column(name = "jd_text", nullable = false, columnDefinition = "text")
    private String jdText;
    @Column(name = "jd_hash", nullable = false, length = 64)
    private String jdHash;
    @Column(name = "dedup_key", nullable = false, length = 64)
    private String dedupKey;
    @Column(nullable = false, length = 20)
    private String source;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "source_url", length = 500)
    private String sourceUrl;
    @Column(length = 500)
    private String note;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "archived_at")
    private Instant archivedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Integer getSalaryMinK() { return salaryMinK; }
    public void setSalaryMinK(Integer salaryMinK) { this.salaryMinK = salaryMinK; }
    public Integer getSalaryMaxK() { return salaryMaxK; }
    public void setSalaryMaxK(Integer salaryMaxK) { this.salaryMaxK = salaryMaxK; }
    public String getJdText() { return jdText; }
    public void setJdText(String jdText) { this.jdText = jdText; }
    public String getJdHash() { return jdHash; }
    public void setJdHash(String jdHash) { this.jdHash = jdHash; }
    public String getDedupKey() { return dedupKey; }
    public void setDedupKey(String dedupKey) { this.dedupKey = dedupKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
