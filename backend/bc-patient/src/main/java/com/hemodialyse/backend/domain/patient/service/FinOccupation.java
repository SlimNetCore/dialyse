package com.hemodialyse.backend.domain.patient.service;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * Règle de libération d'une place de dialyse selon l'état du patient et la date de l'évènement
 * ({@code dateEvenementEtat}). Classe pure du domaine (aucune dépendance Spring ni JPA).
 * <ul>
 *   <li><b>États de sortie</b> ({@code TRANSFERE}, {@code DECEDE}, {@code GREFFE}, {@code GUERRI}) : sans date
 *   d'évènement, la place est libérée immédiatement ; avec une date, le patient occupe sa place <b>jusqu'à cette
 *   date</b> — dernier jour occupé = la date pour un transfert ou une guérison, la veille pour un décès ou une greffe
 *   (le patient ne dialyse plus le jour même) ;</li>
 *   <li><b>États de séjour limité</b> ({@code OCCASIONNEL}, {@code VACANCIER_LOCAL}, {@code VACANCIER_ETRANGER}) : la
 *   date d'évènement est la <b>date de fin de séjour</b> (dernier jour occupé, incluse) ; le séjour commence à la date
 *   d'admission ; sans date de fin, le séjour n'est pas borné ;</li>
 *   <li>l'état {@code PERMANENT} occupe sa place sans limite.</li>
 * </ul>
 */
public final class FinOccupation {

    /**
     * États qui terminent la prise en charge dans le centre.
     */
    public static final Set<String> ETATS_DE_SORTIE = Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI");

    /**
     * États d'un séjour de durée limitée (la date d'évènement est la fin de séjour).
     */
    public static final Set<String> ETATS_SEJOUR_LIMITE = Set.of("OCCASIONNEL", "VACANCIER_LOCAL", "VACANCIER_ETRANGER");

    private static final Set<String> ETATS_LIBERES_LA_VEILLE = Set.of("DECEDE", "GREFFE");

    private FinOccupation() {
    }

    public static boolean etatDeSortie(String etat) {
        return etat != null && ETATS_DE_SORTIE.contains(etat);
    }

    public static boolean sejourLimite(String etat) {
        return etat != null && ETATS_SEJOUR_LIMITE.contains(etat);
    }

    /**
     * Dernier jour où le patient occupe sa place.
     *
     * @return vide si la place est occupée sans limite ; sinon la date, {@link LocalDate#MIN} lorsqu'un état de sortie
     * n'a pas de date d'évènement (place déjà libérée)
     */
    public static Optional<LocalDate> dernierJourOccupe(String etat, LocalDate dateEvenement) {
        if (sejourLimite(etat)) return Optional.ofNullable(dateEvenement);
        if (!etatDeSortie(etat)) return Optional.empty();
        if (dateEvenement == null) return Optional.of(LocalDate.MIN);
        return Optional.of(ETATS_LIBERES_LA_VEILLE.contains(etat) ? dateEvenement.minusDays(1) : dateEvenement);
    }

    /**
     * Le patient occupe-t-il sa place ce jour-là (hors début de séjour : cf. date d'admission) ?
     */
    public static boolean occupeLe(String etat, LocalDate dateEvenement, LocalDate jour) {
        return dernierJourOccupe(etat, dateEvenement).map(fin -> !jour.isAfter(fin)).orElse(true);
    }

    /**
     * La place est-elle définitivement libérée à la date donnée (dernier jour occupé dépassé) ?
     */
    public static boolean placeLiberee(String etat, LocalDate dateEvenement, LocalDate aujourdhui) {
        return dernierJourOccupe(etat, dateEvenement).map(aujourdhui::isAfter).orElse(false);
    }

    /**
     * Cohérence état / date d'évènement à l'enregistrement : un état de sortie exige une date, et aucune date
     * d'évènement ne peut précéder l'admission.
     */
    public static void verifierDate(String etat, LocalDate dateEvenement, LocalDate dateAdmission) {
        if (etatDeSortie(etat) && dateEvenement == null) {
            throw new BusinessException("PATIENT_DATE_EVENEMENT_REQUISE",
                    "La date de l'évènement est obligatoire pour l'état " + etat);
        }
        if (dateEvenement != null && dateAdmission != null && dateEvenement.isBefore(dateAdmission)) {
            throw new BusinessException("PATIENT_DATE_EVENEMENT_AVANT_ADMISSION",
                    "La date de l'évènement ne peut pas précéder la date d'admission");
        }
    }
}
