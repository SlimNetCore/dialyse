package com.hemodialyse.backend.contexts.patientcouverture.domain;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Situation du patient dans le centre : liste FERMÉE reprise du vocabulaire du legacy (décision du 02/11, colonne
 * {@code patients.etat_patient}). Correspondance avec le glossaire : {@code TRANSFERE} = transféré, {@code GUERRI} = guéri,
 * {@code OCCASIONNEL}/{@code VACANCIER_*} = séjour limité. Les transitions ne sont pas contraintes (reproduit, « à décider » :
 * y compris depuis {@code DECEDE}).
 */
public enum EtatDuPatient {
    PERMANENT(null),
    OCCASIONNEL(TypeDeMouvement.SEJOUR_TEMPORAIRE),
    VACANCIER_LOCAL(TypeDeMouvement.SEJOUR_TEMPORAIRE),
    VACANCIER_ETRANGER(TypeDeMouvement.SEJOUR_TEMPORAIRE),
    TRANSFERE(TypeDeMouvement.TRANSFERT),
    DECEDE(TypeDeMouvement.DECES),
    GREFFE(TypeDeMouvement.GREFFE),
    GUERRI(TypeDeMouvement.GUERISON);

    private final TypeDeMouvement mouvement;

    EtatDuPatient(TypeDeMouvement mouvement) {
        this.mouvement = mouvement;
    }

    /** Lit un état saisi ; refus 422 {@code PATIENT_ETAT_INCONNU} hors liste (casse comprise). */
    public static EtatDuPatient depuis(String texte) {
        if (texte == null) throw new RegleDePatientViolee("PATIENT_ETAT_INCONNU", "État du patient inconnu");
        try {
            return valueOf(texte);
        } catch (IllegalArgumentException e) {
            throw new RegleDePatientViolee("PATIENT_ETAT_INCONNU", "État du patient inconnu : " + texte);
        }
    }

    /** Termine la prise en charge dans le centre. */
    public boolean sortie() {
        return this == TRANSFERE || this == DECEDE || this == GREFFE || this == GUERRI;
    }

    /** Séjour de durée limitée : la date d'évènement est la fin de séjour. */
    public boolean sejourLimite() {
        return this == OCCASIONNEL || this == VACANCIER_LOCAL || this == VACANCIER_ETRANGER;
    }

    public boolean vacancier() {
        return this == VACANCIER_LOCAL || this == VACANCIER_ETRANGER;
    }

    /** Mouvement tracé quand le patient prend cet état ({@code REPRISE} pour {@code PERMANENT}). */
    public TypeDeMouvement mouvement() {
        return mouvement == null ? TypeDeMouvement.REPRISE : mouvement;
    }

    /**
     * Dernier jour où le patient occupe sa place : vide si sans limite ; {@link LocalDate#MIN} pour une sortie sans date
     * (place déjà libérée) ; la veille de la date pour un décès ou une greffe.
     */
    public Optional<LocalDate> dernierJourOccupe(LocalDate dateEvenement) {
        if (sejourLimite()) return Optional.ofNullable(dateEvenement);
        if (!sortie()) return Optional.empty();
        if (dateEvenement == null) return Optional.of(LocalDate.MIN);
        return Optional.of(this == DECEDE || this == GREFFE ? dateEvenement.minusDays(1) : dateEvenement);
    }
}
