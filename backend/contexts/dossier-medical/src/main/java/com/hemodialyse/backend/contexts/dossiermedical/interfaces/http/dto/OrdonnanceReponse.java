package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Une ordonnance, champ par champ comme le legacy. */
public record OrdonnanceReponse(UUID id, UUID patientId, UUID centerId, String medecinId, LocalDate datePrescription, String statut,
                                String numero, List<LigneReponse> lignes, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                OffsetDateTime signedAt) {

    public record LigneReponse(UUID id, String codeSystem, String code, String codeDisplay, String libelle, String posologie,
                               String voie, Integer dureeJours, Integer quantite, String instructions) {
        static LigneReponse de(LigneDOrdonnance l) {
            var m = l.medicament();
            return new LigneReponse(l.id(), m == null ? null : m.nomenclature(), m == null ? null : m.code(),
                    m == null ? null : m.libelle(), l.libelle(), l.posologie(), l.voie(), l.dureeJours(), l.quantite(), l.instructions());
        }
    }

    public static OrdonnanceReponse de(Ordonnance o) {
        return new OrdonnanceReponse(o.id(), o.patient().valeur(), o.centre().valeur(), o.medecin(), o.datePrescription(),
                o.statut().name(), o.numero(), o.lignes().stream().map(LigneReponse::de).toList(), horodatage(o.creeeLe()),
                horodatage(o.modifieeLe()), horodatage(o.signeeLe()));
    }

    private static OffsetDateTime horodatage(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
