package com.hemodialyse.backend.application.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PatientSummaryQueryService {

    private final JdbcTemplate jdbc;

    public PatientSummaryQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static boolean shouldIncludeInSummary(Map<String, Object> row, YearMonth referenceMonth) {
        String etatPatient = resolvePatientState(row.get("etat_patient"));
        if ("PERMANENT".equals(etatPatient)) {
            return true;
        }
        LocalDate eventDate = toLocalDate(row.get("date_evenement_etat"));
        return eventDate != null && YearMonth.from(eventDate).equals(referenceMonth);
    }

    private static void increment(Map<String, Long> counts, String code) {
        counts.computeIfPresent(code, (key, value) -> value + 1L);
    }

    private static String resolveSexBucket(Object raw) {
        String value = normalize(raw);
        if (value.isBlank()) {
            return "AUTRE";
        }
        return switch (value) {
            case "M", "MASCULIN", "H", "HOMME" -> "M";
            case "F", "FEMININ", "FÉMININ", "FEMME" -> "F";
            default -> "AUTRE";
        };
    }

    private static String resolveAgeBucket(Object rawDate, LocalDate today) {
        LocalDate birthDate = toLocalDate(rawDate);
        if (birthDate == null || birthDate.isAfter(today)) {
            return "INCONNU";
        }

        int age = Period.between(birthDate, today).getYears();
        if (age < 18) return "0_17";
        if (age < 30) return "18_29";
        if (age < 45) return "30_44";
        if (age < 60) return "45_59";
        return "60_PLUS";
    }

    private static String resolveKtBucket(Object raw) {
        if (raw instanceof Boolean value) {
            return value ? "OUI" : "NON";
        }
        String value = normalize(raw);
        if (value.isBlank()) {
            return "NON";
        }
        if (List.of("1", "TRUE", "OUI", "YES", "Y").contains(value)) {
            return "OUI";
        }
        return "NON";
    }

    private static String resolvePatientState(Object raw) {
        String value = normalize(raw);
        return value.isBlank() ? "PERMANENT" : value;
    }

    private static LocalDate toLocalDate(Object raw) {
        return switch (raw) {
            case null -> null;
            case LocalDate date -> date;
            case java.sql.Date sqlDate -> sqlDate.toLocalDate();
            case java.util.Date utilDate -> utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            default -> {
                String value = raw.toString().trim();
                if (value.isBlank()) {
                    yield null;
                }
                try {
                    yield LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
                } catch (Exception ignored) {
                    yield null;
                }
            }
        };
    }

    private static String normalize(Object raw) {
        return raw == null ? "" : raw.toString().trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeDisplay(Object raw) {
        return raw == null ? "" : raw.toString().trim();
    }

    private static List<SummaryBucket> toBuckets(Map<String, Long> counts, List<BucketDefinition> order) {
        List<SummaryBucket> buckets = new ArrayList<>();
        for (BucketDefinition def : order) {
            buckets.add(new SummaryBucket(def.code(), def.label(), counts.getOrDefault(def.code(), 0L)));
        }
        return buckets;
    }

    public PatientSummaryResponse getSummary(UUID centerId) {
        return getSummary(centerId, YearMonth.now());
    }

    public PatientSummaryResponse getSummary(UUID centerId, YearMonth referenceMonth) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT sexe, date_naissance, sous_kt, etat_patient, date_evenement_etat FROM patients WHERE center_id = ?",
                centerId
        );

        Map<String, Long> sexCounts = new LinkedHashMap<>();
        sexCounts.put("M", 0L);
        sexCounts.put("F", 0L);
        sexCounts.put("AUTRE", 0L);

        Map<String, Long> ageCounts = new LinkedHashMap<>();
        ageCounts.put("0_17", 0L);
        ageCounts.put("18_29", 0L);
        ageCounts.put("30_44", 0L);
        ageCounts.put("45_59", 0L);
        ageCounts.put("60_PLUS", 0L);
        ageCounts.put("INCONNU", 0L);

        Map<String, Long> ktCounts = new LinkedHashMap<>();
        ktCounts.put("OUI", 0L);
        ktCounts.put("NON", 0L);

        LocalDate referenceDate = referenceMonth.atEndOfMonth();
        long totalPatients = 0;
        for (Map<String, Object> row : rows) {
            if (!shouldIncludeInSummary(row, referenceMonth)) {
                continue;
            }
            totalPatients++;
            increment(sexCounts, resolveSexBucket(row.get("sexe")));
            increment(ageCounts, resolveAgeBucket(row.get("date_naissance"), referenceDate));
            increment(ktCounts, resolveKtBucket(row.get("sous_kt")));
        }

        return new PatientSummaryResponse(
                totalPatients,
                toBuckets(sexCounts, List.of(
                        new BucketDefinition("M", "Masculin"),
                        new BucketDefinition("F", "Féminin"),
                        new BucketDefinition("AUTRE", "Autre / inconnu")
                )),
                toBuckets(ageCounts, List.of(
                        new BucketDefinition("0_17", "0-17 ans"),
                        new BucketDefinition("18_29", "18-29 ans"),
                        new BucketDefinition("30_44", "30-44 ans"),
                        new BucketDefinition("45_59", "45-59 ans"),
                        new BucketDefinition("60_PLUS", "60 ans et +"),
                        new BucketDefinition("INCONNU", "Non renseigné")
                )),
                toBuckets(ktCounts, List.of(
                        new BucketDefinition("OUI", "Sous KT"),
                        new BucketDefinition("NON", "Sans KT")
                ))
        );
    }

    public PatientSummaryDetailsResponse getSummaryDetails(UUID centerId, YearMonth referenceMonth) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                """
                        SELECT id,
                               code_patient,
                               nom,
                               prenom,
                               sexe,
                               etat_patient,
                               date_evenement_etat,
                               date_admission,
                               sous_kt
                        FROM patients
                        WHERE center_id = ?
                        """,
                centerId
        );

        List<PatientSummaryDetailItem> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!shouldIncludeInSummary(row, referenceMonth)) {
                continue;
            }

            UUID id = (UUID) row.get("id");
            String state = resolvePatientState(row.get("etat_patient"));
            LocalDate eventDate = toLocalDate(row.get("date_evenement_etat"));
            LocalDate admissionDate = toLocalDate(row.get("date_admission"));
            String inclusionReason = "PERMANENT".equals(state) ? "PERMANENT" : "EVENT_MONTH";

            items.add(new PatientSummaryDetailItem(
                    id,
                    normalizeDisplay(row.get("code_patient")),
                    normalizeDisplay(row.get("nom")),
                    normalizeDisplay(row.get("prenom")),
                    resolveSexBucket(row.get("sexe")),
                    state,
                    eventDate,
                    admissionDate,
                    "OUI".equals(resolveKtBucket(row.get("sous_kt"))),
                    inclusionReason
            ));
        }

        items.sort(Comparator
                .comparing((PatientSummaryDetailItem i) -> Objects.toString(i.nom(), ""))
                .thenComparing(i -> Objects.toString(i.prenom(), ""))
                .thenComparing(i -> Objects.toString(i.codePatient(), "")));

        return new PatientSummaryDetailsResponse(referenceMonth.toString(), items.size(), items);
    }

    private record BucketDefinition(String code, String label) {
    }

    public record PatientSummaryResponse(long totalPatients, List<SummaryBucket> sexDistribution,
                                         List<SummaryBucket> ageDistribution, List<SummaryBucket> ktDistribution) {
    }

    public record SummaryBucket(String code, String label, long count) {
    }

    public record PatientSummaryDetailsResponse(String month, long total, List<PatientSummaryDetailItem> items) {
    }

    public record PatientSummaryDetailItem(UUID patientId,
                                           String codePatient,
                                           String nom,
                                           String prenom,
                                           String sexe,
                                           String etatPatient,
                                           LocalDate dateEvenementEtat,
                                           LocalDate dateAdmission,
                                           boolean sousKt,
                                           String inclusionReason) {
    }
}



