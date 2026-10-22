package com.hemodialyse.backend.contexts.absencespatients.interfaces.http;

import com.hemodialyse.backend.contexts.absencespatients.application.LigneDuSuivi;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une absence de la liste : noms JSON inchangés ({@code prixTtc} est le montant TTC perdu, RG-ABS-030).
 */
public record AbsenceReponse(UUID id, UUID patientId, String patientNom, LocalDate dateSeance, String source,
                             String statut, String motif, String commentaire, String forfaitLibelle,
                             BigDecimal prixTtc, BigDecimal tauxTva, BigDecimal montantHt, Instant declareeLe,
                             Instant qualifieeLe, LocalDate dateRattrapage, boolean enRetard) {

    static AbsenceReponse depuis(LigneDuSuivi ligne) {
        var a = ligne.absence();
        return new AbsenceReponse(a.id().valeur(), a.patient().valeur(), ligne.nomDuPatient(), a.dateDeSeance(),
                a.source().name(), a.statut().name(), a.motif() == null ? null : a.motif().name(), a.commentaire(),
                a.libelleDuForfait(), a.montantTtc(), a.tauxDeTva(), a.montantHt(), a.declareeLe(), a.qualifieeLe(),
                a.dateDeRattrapage(), ligne.enRetard());
    }
}
