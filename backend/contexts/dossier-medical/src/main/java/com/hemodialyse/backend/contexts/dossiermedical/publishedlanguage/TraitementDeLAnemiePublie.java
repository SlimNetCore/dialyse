package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : les administrations de traitement de l'anémie (EPO, fer injectable) des centres
 * d'une société sur une période, pour la répartition du Pilotage de la Direction ({@code DirectionBreakdownQueryService}). Comptes BRUTS
 * (le Pilotage masque les effectifs de patients avec sa propre politique d'anonymat) ; aucune donnée nominative. Un centre sans
 * administration sur la période est absent de la carte.
 */
public interface TraitementDeLAnemiePublie {

    Map<UUID, AdministrationsDuCentre> parCentre(List<UUID> centres, LocalDate du, LocalDate au);

    /**
     * @param patientsEpo       patients distincts ayant reçu au moins une administration EFFECTIVE d'EPO
     * @param patientsFer       idem pour le fer injectable
     * @param administreesEpo   administrations effectives d'EPO
     * @param administreesFer   administrations effectives de fer injectable
     * @param nonAdministrees   administrations non effectuées (tout traitement)
     */
    record AdministrationsDuCentre(long patientsEpo, long patientsFer, long administreesEpo, long administreesFer, long nonAdministrees) {
    }
}
