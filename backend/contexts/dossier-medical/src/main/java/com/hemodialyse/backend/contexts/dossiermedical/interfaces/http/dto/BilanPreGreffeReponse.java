package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DecisionRcp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse du bilan pré-greffe (mêmes champs que le legacy : PRA renvoyé tel que saisi, non arrondi par la colonne). */
public record BilanPreGreffeReponse(UUID id, UUID patientId, UUID centerId, String statut, LocalDate dateDebutBilan,
                                    LocalDate dateInscriptionListeAttente, LocalDate dateGreffe, String groupeSanguinConfirme, String typageHla,
                                    BigDecimal praClasseI, BigDecimal praClasseII, String contreIndications, String conclusionNephrologue,
                                    List<DecisionRcpReponse> decisionsRcp, OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public record DecisionRcpReponse(UUID id, LocalDate dateReunion, String avis, String compteRendu, LocalDate prochaineDateRevue) {
        static DecisionRcpReponse de(DecisionRcp d) {
            return new DecisionRcpReponse(d.id(), d.dateReunion(), d.avis().name(), d.compteRendu(), d.prochaineDateRevue());
        }
    }

    public static BilanPreGreffeReponse de(BilanPreGreffe b) {
        return new BilanPreGreffeReponse(b.id(), b.patient().valeur(), b.centre().valeur(), b.statut().name(), b.dateDebutBilan(),
                b.dateInscriptionListeAttente(), b.dateGreffe(), b.groupeSanguinConfirme(), b.typageHla(), b.praClasseI(), b.praClasseII(),
                b.contreIndications(), b.conclusionNephrologue(), b.decisions().stream().map(DecisionRcpReponse::de).toList(),
                horodatage(b.creeLe()), horodatage(b.modifieLe()));
    }
}
