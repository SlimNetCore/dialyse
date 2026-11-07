package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Une observation biologique, champ par champ comme le legacy. */
public record ObservationReponse(UUID id, UUID patientId, UUID centerId, UUID demandeExamenId, String codeSystem, String code,
                                 String codeDisplay, BigDecimal valeurNum, String unite, String valeurTexte, LocalDate datePrelevement,
                                 String statut, String source, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static ObservationReponse de(ObservationBiologique o) {
        var valeur = o.valeurNumerique();
        return new ObservationReponse(o.id(), o.patient().valeur(), o.centre().valeur(), o.demandeDExamen(), o.analyte().systeme().name(),
                o.analyte().code(), o.analyte().affichage(), valeur == null ? null : valeur.valeur(), valeur == null ? null : valeur.unite(),
                o.valeurTexte(), o.datePrelevement(), o.statut().name(), o.source().name(), horodatage(o.creeLe()), horodatage(o.modifieLe()));
    }
}
