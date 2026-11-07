package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Un résultat sérologique, champ par champ comme le legacy. */
public record SerologieReponse(UUID id, UUID patientId, UUID centerId, String marqueur, String resultat, BigDecimal titre, String unite,
                               LocalDate datePrelevement, String laboratoire, LocalDate dateProchainControle, String conduiteATenir,
                               OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public static SerologieReponse de(Serologie s) {
        return new SerologieReponse(s.id(), s.patient().valeur(), s.centre().valeur(), s.marqueur().name(), s.resultat().name(), s.titre(),
                s.unite(), s.datePrelevement(), s.laboratoire(), s.dateProchainControle(), s.conduiteATenir(), horodatage(s.creeeLe()),
                horodatage(s.modifieeLe()));
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
