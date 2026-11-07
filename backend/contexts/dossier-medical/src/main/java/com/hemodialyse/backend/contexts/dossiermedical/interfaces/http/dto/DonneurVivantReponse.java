package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneurVivant;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto.Horodatages.horodatage;

/** Réponse d'un donneur vivant (mêmes champs que le legacy). */
public record DonneurVivantReponse(UUID id, UUID patientId, UUID centerId, String nom, String prenom, LocalDate dateNaissance, String lienParente,
                                   String telephone, String groupeSanguin, String typageHla, String statutBilan, String crossmatchResultat,
                                   LocalDate dateCrossmatch, String bilanRealise, String contreIndications, String decisionFinale,
                                   LocalDate dateDecision, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public static DonneurVivantReponse de(DonneurVivant d) {
        return new DonneurVivantReponse(d.id(), d.patient().valeur(), d.centre().valeur(), d.nom(), d.prenom(), d.dateNaissance(),
                d.lienDeParente().name(), d.telephone(), d.groupeSanguin(), d.typageHla(), d.statutBilan().name(), d.crossmatch().name(),
                d.dateCrossmatch(), d.bilanRealise(), d.contreIndications(), d.decisionFinale(), d.dateDecision(), horodatage(d.creeLe()),
                horodatage(d.modifieLe()));
    }
}
