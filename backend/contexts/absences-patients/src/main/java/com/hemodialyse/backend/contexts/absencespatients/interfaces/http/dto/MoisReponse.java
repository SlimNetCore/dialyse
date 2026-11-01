package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;
import com.hemodialyse.backend.contexts.absencespatients.domain.MoisAbsences;

import java.util.List;
import java.util.UUID;

/**
 * Les 12 mois d'une année. {@code statut} vaut {@code OUVERT} ou {@code CLOTURE} ; les champs de dernière clôture et de dernière
 * réouverture restent renseignés après une réouverture (l'historique complet est au journal).
 */
public record MoisReponse(UUID centerId, int annee, List<Mois> mois) {

    public static MoisReponse depuis(CentreId centre, int annee, List<MoisAbsences> mois, boolean complet) {
        return new MoisReponse(centre.valeur(), annee, mois.stream().map(m -> new Mois(m.mois().toString(),
                m.estClos() ? "CLOTURE" : "OUVERT", complet ? id(m.closPar()) : null, m.closLe(),
                complet ? id(m.rouvertPar()) : null, m.rouvertLe(), complet ? m.motifDeReouverture() : null)).toList());
    }

    private static UUID id(UtilisateurId utilisateur) {
        return utilisateur == null ? null : utilisateur.valeur();
    }
}
