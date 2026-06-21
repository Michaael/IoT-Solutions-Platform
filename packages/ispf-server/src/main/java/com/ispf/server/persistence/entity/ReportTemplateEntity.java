package com.ispf.server.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "report_templates")
public class ReportTemplateEntity {

    @Id
    @Column(name = "report_path", nullable = false, length = 512)
    private String reportPath;

    @Column(nullable = false, length = 16)
    private String format;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false)
    private byte[] content;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public String getReportPath() {
        return reportPath;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
