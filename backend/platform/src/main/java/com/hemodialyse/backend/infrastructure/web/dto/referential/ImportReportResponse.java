package com.hemodialyse.backend.infrastructure.web.dto.referential;

import com.hemodialyse.backend.domain.referential.admin.model.ImportReport;
import com.hemodialyse.backend.domain.referential.admin.model.ValidationIssue;

import java.util.List;

public record ImportReportResponse(String kind, int totalRows, int created, int updated,
                                   List<String> missingColumns, List<String> ignoredColumns,
                                   List<ValidationIssue> errors, boolean valid, boolean applied) {

    public static ImportReportResponse from(ImportReport report) {
        return new ImportReportResponse(report.kind(), report.totalRows(), report.created(), report.updated(),
                report.missingColumns(), report.ignoredColumns(), report.errors(), report.isValid(), report.applied());
    }
}

