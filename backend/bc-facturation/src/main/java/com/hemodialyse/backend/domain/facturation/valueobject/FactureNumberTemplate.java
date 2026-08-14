package com.hemodialyse.backend.domain.facturation.valueobject;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record FactureNumberTemplate(String value) {

    private static final Pattern SEQUENCE_TOKEN_PATTERN = Pattern.compile("\\{SEQ(?:(\\d+))?}");
    private static final int DEFAULT_SEQUENCE_WIDTH = 4;

    public FactureNumberTemplate {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le code de facturation est obligatoire");
        }
        value = value.trim();
    }

    public String format(CenterId centerId, LocalDate billingDate, int sequence) {
        if (centerId == null) {
            throw new IllegalArgumentException("Le centre est obligatoire");
        }
        if (billingDate == null) {
            throw new IllegalArgumentException("La date de facturation est obligatoire");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("La sequence doit etre strictement positive");
        }

        String invoiceNumber = value
                .replace("{YYYY}", String.valueOf(billingDate.getYear()))
                .replace("{YEAR}", String.valueOf(billingDate.getYear()))
                .replace("{YY}", String.format("%02d", billingDate.getYear() % 100))
                .replace("{CENTER}", centerId.value().toString().substring(0, 8).toUpperCase());

        Matcher matcher = SEQUENCE_TOKEN_PATTERN.matcher(invoiceNumber);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            int width = matcher.group(1) == null ? DEFAULT_SEQUENCE_WIDTH : Integer.parseInt(matcher.group(1));
            String formattedSequence = String.format("%0" + width + "d", sequence);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(formattedSequence));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
