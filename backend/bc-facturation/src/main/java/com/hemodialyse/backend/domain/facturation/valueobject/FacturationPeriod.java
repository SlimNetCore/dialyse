package com.hemodialyse.backend.domain.facturation.valueobject;

import java.time.LocalDate;
import java.time.YearMonth;

public record FacturationPeriod(LocalDate startDate, LocalDate endDate) {
    public FacturationPeriod {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("La periode de facturation est obligatoire");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La date de fin ne peut pas etre avant la date de debut");
        }
    }

    public static FacturationPeriod ofMonth(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("Le mois est obligatoire");
        }
        return new FacturationPeriod(month.atDay(1), month.atEndOfMonth());
    }
}

