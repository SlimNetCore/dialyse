package com.hemodialyse.backend.contexts.seance.domain;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Séances prévues d'un mois : pour chaque patient pris en charge qui n'est pas en sommeil, les jours de dialyse de sa période de
 * présence (de son admission à la libération de sa place, bornée à la période), hors jours non ouvrés du centre.
 *
 * @param prevues            nombre de séances prévues
 * @param patientsConsideres patients dont la période de présence n'est pas vide
 * @param parJourDeSemaine   KB-SEA-62 (reproduit, à ne pas « réparer » sans le front) : sept clés françaises {@code LUNDI} à
 *                           {@code DIMANCHE} à 0, puis les comptes sur les clés anglaises ({@code MONDAY}...) dans l'ordre de rencontre
 */
public record SeancesAttendues(long prevues, long patientsConsideres, Map<String, Long> parJourDeSemaine) {

    private static final List<String> JOURS_FRANCAIS = List.of("LUNDI", "MARDI", "MERCREDI", "JEUDI", "VENDREDI", "SAMEDI", "DIMANCHE");

    /**
     * @param debut premier jour du mois
     * @param fin   dernier jour échu de la période (inférieur au début si rien n'est échu)
     */
    public static SeancesAttendues calculer(List<PatientDuMois> patients, LocalDate debut, LocalDate fin, Set<LocalDate> joursBloques) {
        long prevues = 0;
        long consideres = 0;
        Map<String, Long> parJour = new LinkedHashMap<>();
        for (String jour : JOURS_FRANCAIS) parJour.put(jour, 0L);
        for (PatientDuMois patient : patients) {
            if (patient.enSommeil()) continue;
            LocalDate premier = patient.premierJourAttendu(debut);
            LocalDate dernier = patient.dernierJourAttendu(fin);
            if (premier.isAfter(dernier)) continue;
            consideres++;
            for (LocalDate jour = premier; !jour.isAfter(dernier); jour = jour.plusDays(1)) {
                if (joursBloques.contains(jour)) continue;
                if (patient.dialyseLe(jour)) {
                    prevues++;
                    parJour.merge(jour.getDayOfWeek().name(), 1L, Long::sum);
                }
            }
        }
        return new SeancesAttendues(prevues, consideres, parJour);
    }
}
