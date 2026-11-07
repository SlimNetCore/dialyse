package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDeDemandeDExamen;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Une demande d'examen, champ par champ comme le legacy. */
public record DemandeDExamenReponse(UUID id, UUID patientId, UUID centerId, String prescripteurId, LocalDate dateDemande, String categorie,
                                    boolean urgent, String motif, String statut, String conclusion, List<LigneReponse> lignes,
                                    OffsetDateTime createdAt, OffsetDateTime updatedAt) {

    public record LigneReponse(UUID id, String codeSystem, String code, String codeDisplay, String libelle, String commentaire) {
        static LigneReponse de(LigneDeDemandeDExamen l) {
            var c = l.code();
            return new LigneReponse(l.id(), c == null ? null : c.nomenclature(), c == null ? null : c.code(), c == null ? null : c.libelle(),
                    l.libelle(), l.commentaire());
        }
    }

    public static DemandeDExamenReponse de(DemandeExamen d) {
        return new DemandeDExamenReponse(d.id(), d.patient().valeur(), d.centre().valeur(), d.prescripteur(), d.dateDemande(),
                d.categorie().name(), d.urgent(), d.motif(), d.statut().name(), d.conclusion(),
                d.lignes().stream().map(LigneReponse::de).toList(), horodatage(d.creeeLe()), horodatage(d.modifieeLe()));
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
