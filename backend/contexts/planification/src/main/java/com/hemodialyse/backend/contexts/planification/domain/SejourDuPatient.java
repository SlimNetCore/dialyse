package com.hemodialyse.backend.contexts.planification.domain;

import java.time.LocalDate;

/**
 * Le séjour d'un patient dans le centre, vu de la planification : <b>l'unique calcul de présence</b> partagé par le planning de la
 * semaine, les patients attendus (Absences) et la file active (Direction) (RG-PLN-045, KB-PLN-71).
 * <p>
 * Trois questions, trois noms : {@link #occupeSaPlaceLe} (la place est tenue : admission commencée, dernier jour non dépassé, le
 * sommeil n'y change rien : un patient en sommeil garde sa place pour éviter un double placement), {@link #estPresentLe} (la place
 * est tenue et le patient n'est pas en sommeil : il figure au planning) et {@link #estAttenduLe} (présent, et pas parti ce jour-là :
 * une séance est attendue).
 *
 * @param admission            premier jour du séjour ; nul : sans borne (une fiche sans date d'admission est comptée)
 * @param dernierJourDePlace   dernier jour où la place est tenue ; nul : sans limite ; {@link LocalDate#MIN} : place déjà libérée
 * @param partLeDernierJour    transféré ou guéri : le dernier jour est celui du départ, aucune séance n'y est attendue
 * @param enSommeil            patient suspendu : hors planning, hors file active, jamais attendu
 */
public record SejourDuPatient(LocalDate admission, LocalDate dernierJourDePlace, boolean partLeDernierJour, boolean enSommeil) {

    /** Permanent, admis depuis toujours, sans sortie ni sommeil. */
    public static final SejourDuPatient SANS_LIMITE = new SejourDuPatient(null, null, false, false);

    public boolean occupeSaPlaceLe(LocalDate jour) {
        return (admission == null || !jour.isBefore(admission)) && (dernierJourDePlace == null || !jour.isAfter(dernierJourDePlace));
    }

    public boolean estPresentLe(LocalDate jour) {
        return !enSommeil && occupeSaPlaceLe(jour);
    }

    public boolean estAttenduLe(LocalDate jour) {
        return estPresentLe(jour) && !(partLeDernierJour && jour.equals(dernierJourDePlace));
    }

    /** Présent au moins un jour de la période (bornes incluses) : la file active de la direction (RG-PAT-032). */
    public boolean estPresentSur(LocalDate du, LocalDate au) {
        return !enSommeil && (admission == null || !admission.isAfter(au)) && (dernierJourDePlace == null || !dernierJourDePlace.isBefore(du));
    }

    /** La place est déjà libérée avant ce jour (patient sorti) : elle ne compte plus dans une période qui commence ce jour. */
    public boolean placeLibereeAvant(LocalDate jour) {
        return dernierJourDePlace != null && jour.isAfter(dernierJourDePlace);
    }
}
