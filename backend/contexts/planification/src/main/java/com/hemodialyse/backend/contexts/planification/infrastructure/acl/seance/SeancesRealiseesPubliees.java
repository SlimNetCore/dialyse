package com.hemodialyse.backend.contexts.planification.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.planification.application.SeancesRealisees;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.SemaineDuPlanning.SeanceRealisee;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies.SeanceRealiseeAvecPlaceFournie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * ACL de la Planification vers la Séance (ADR 0098, SE7d) : les séances réalisées d'une période, avec la place figée à la validation, viennent
 * de {@link SeancesRealiseesAvecLeurPlaceFournies} (implémenté par la plateforme sur {@code seance.v1}). Traductions reprises de
 * {@code legacyseance/SeancesRealiseesJdbc}, qu'elle remplace : nom « prénom nom » rogné (une partie nulle compte pour vide), place nulle
 * conservée, patient à risque infectieux. Les patients à risque sont lus AVANT les séances, comme avant. Triées par date seule (ordre de deux
 * séances d'une même date non défini, KB-SEA-78) ; un patient sorti garde son nom sur ses séances passées.
 */
public class SeancesRealiseesPubliees implements SeancesRealisees {

    private final SeancesRealiseesAvecLeurPlaceFournies seances;
    private final Function<UUID, Set<UUID>> patientsARisque;

    /**
     * @param patientsARisque les patients à risque infectieux du centre (aujourd'hui {@code RisquesInfectieuxJdbc.patientsARisque}, ACL du
     *                        dossier médical)
     */
    public SeancesRealiseesPubliees(SeancesRealiseesAvecLeurPlaceFournies seances, Function<UUID, Set<UUID>> patientsARisque) {
        this.seances = seances;
        this.patientsARisque = patientsARisque;
    }

    @Override
    public List<SeanceRealisee> duCentre(CentreId centre, LocalDate du, LocalDate au) {
        Set<UUID> aRisque = patientsARisque.apply(centre.valeur());
        return seances.duCentre(centre.valeur(), du, au).stream().map(s -> traduire(s, aRisque)).toList();
    }

    static SeanceRealisee traduire(SeanceRealiseeAvecPlaceFournie s, Set<UUID> aRisque) {
        return new SeanceRealisee(new PatientId(s.patient()), nom(s.prenom(), s.nom()), s.date(),
                s.salle() == null ? null : new SalleId(s.salle()), s.creneau() == null ? null : new CreneauId(s.creneau()),
                s.generateur() == null ? null : new GenerateurId(s.generateur()), aRisque.contains(s.patient()));
    }

    /** « prénom nom » rogné ; une partie nulle compte pour vide (branche inatteignable aujourd'hui : {@code prenom} est NOT NULL). */
    static String nom(String prenom, String nom) {
        return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
    }
}
