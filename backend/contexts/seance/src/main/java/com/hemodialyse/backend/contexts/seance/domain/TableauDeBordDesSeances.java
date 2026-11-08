package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.Map;

/**
 * Tableau de bord mensuel des séances d'un centre : séances prévues, présences (séances réalisées : validées, signées ou facturées),
 * absences du suivi (hors annulées) et effectif, avec leurs répartitions. Aucun montant.
 * <p>
 * KB-SEA-64 (reproduit) : les compteurs ne se recoupent pas avec le détail nominatif : {@code presences} compte les séances,
 * {@code absences} les absences du suivi, et le détail écarte les patients introuvables dans le centre ; un patient peut être présent et
 * absent le même jour. {@code seancesTotales} est toujours égal à {@code presences} (reproduit).
 *
 * @param periodeFin      dernier jour échu de la période, ramené au premier jour du mois si rien n'est échu
 * @param joursBloques    nombre de jours non ouvrés du mois (fériés et fermetures exceptionnelles, dédoublonnés)
 */
public record TableauDeBordDesSeances(MoisDuTableauDeBord mois, long seancesPrevues, long presences, long absences,
                                      EffectifDuMois effectif, SeancesAttendues attendues, LocalDate periodeDebut, LocalDate periodeFin,
                                      int joursBloques) {

    /** Total des séances : égal aux présences (reproduit). */
    public long seancesTotales() {
        return presences;
    }

    public static TableauDeBordDesSeances composer(MoisDuTableauDeBord mois, LocalDate aujourdhui, long presences, long absences,
                                                   SeancesAttendues attendues, EffectifDuMois effectif, int joursBloques) {
        LocalDate debut = mois.premierJour();
        LocalDate limite = mois.dernierJourEchu(aujourdhui);
        return new TableauDeBordDesSeances(mois, attendues.prevues(), presences, absences, effectif, attendues, debut,
                limite.isBefore(debut) ? debut : limite, joursBloques);
    }
}
