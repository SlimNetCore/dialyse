package com.hemodialyse.backend.contexts.absencespatients.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié (lecture) : les absences de patients telles que le pilotage les consomme (tableau de bord des séances d'un centre,
 * vue de la Direction d'une société). Bornes de dates incluses, sur la date de la séance manquée. Les absences annulées ne sont
 * jamais comptées. Aucune donnée nominative : seul le détail d'un centre expose les identifiants de patients, à l'appelant de les
 * résoudre. Aucune isolation de société ni de centre ici : l'appelant fournit les centres autorisés.
 */
public interface AbsencesDuPilotage {

    /** Le jour d'aujourd'hui dans le fuseau des centres (décision 7 : pas l'UTC). */
    LocalDate aujourdhui();

    /** Le nombre d'absences du centre sur la période, hors annulées. */
    long nombreHorsAnnulees(UUID centre, LocalDate du, LocalDate au);

    /** Les absences du centre sur la période, hors annulées (sans ordre garanti). */
    List<AbsenceDuCentre> horsAnnulees(UUID centre, LocalDate du, LocalDate au);

    /** Les absences hors annulées regroupées par centre, statut et motif (un groupe par triplet existant, sans ordre garanti). */
    List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Les absences à qualifier, justifiées ou non justifiées de chaque mois, tous centres confondus, par mois croissant. */
    List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au);

    /** Une absence : le patient, le jour de la séance manquée et son statut ({@code A_QUALIFIER}, {@code JUSTIFIEE}, ...). */
    record AbsenceDuCentre(UUID patient, LocalDate dateDeSeance, String statut) {
    }

    /** Un groupe : motif nul tant que l'absence n'est pas qualifiée ; montants cumulés (zéro à défaut). */
    record CumulDuCentre(UUID centre, String statut, String motif, long nombre, BigDecimal montantHt, BigDecimal montantTtc) {
    }

    record AbsencesDuMois(int annee, int mois, long nombre, BigDecimal montantHt) {
    }
}
