package com.hemodialyse.backend.contexts.absencespatients.domain;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.UtilisateurId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Racine d'agrégat : le mois d'absences d'un centre, ouvert ou clos (clôture mensuelle des absences, A6, ADR 0042 qui réalise l'ADR
 * 0009). Tant que le mois est clos, aucune absence datée de ce mois ne se déclare, se qualifie, se rattrape, s'annule ni ne se
 * détecte, et l'exclusion d'une séance de la Facturation ne peut plus y créer d'absence. Clôturer ne demande aucune précondition
 * d'état (l'administrateur décide, un avertissement chiffré est tracé) ; rouvrir exige un motif. Un mois sans ligne est ouvert.
 * <p>
 * L'agrégat retient la dernière clôture et la dernière réouverture ; chaque acte renvoie son entrée du journal, qui ne se modifie
 * jamais (append-only). Que le mois soit terminé (jamais le mois en cours ni un mois futur) se juge avec l'horloge du centre, donc
 * au cas d'usage et pas ici : le domaine ne lit pas l'heure.
 */
public final class MoisAbsences {

    private final CentreId centre;
    private final YearMonth mois;
    private boolean clos;
    private Instant closLe;
    private UtilisateurId closPar;
    private Instant rouvertLe;
    private UtilisateurId rouvertPar;
    private String motifDeReouverture;

    @SuppressWarnings("java:S107")
    private MoisAbsences(CentreId centre, YearMonth mois, boolean clos, Instant closLe, UtilisateurId closPar, Instant rouvertLe,
                         UtilisateurId rouvertPar, String motifDeReouverture) {
        if (centre == null || mois == null) {
            throw new IllegalArgumentException("Le centre et le mois sont obligatoires");
        }
        this.centre = centre;
        this.mois = mois;
        this.clos = clos;
        this.closLe = closLe;
        this.closPar = closPar;
        this.rouvertLe = rouvertLe;
        this.rouvertPar = rouvertPar;
        this.motifDeReouverture = motifDeReouverture;
    }

    /**
     * Un mois qui n'a jamais été clos.
     */
    public static MoisAbsences ouvert(CentreId centre, YearMonth mois) {
        return new MoisAbsences(centre, mois, false, null, null, null, null, null);
    }

    /**
     * Un mois tel qu'il est enregistré (adaptateur de persistance) : aucune règle n'est rejouée.
     */
    @SuppressWarnings("java:S107")
    public static MoisAbsences reconstituer(CentreId centre, YearMonth mois, boolean clos, Instant closLe, UtilisateurId closPar,
                                            Instant rouvertLe, UtilisateurId rouvertPar, String motifDeReouverture) {
        return new MoisAbsences(centre, mois, clos, closLe, closPar, rouvertLe, rouvertPar, motifDeReouverture);
    }

    private static void exigerUnActe(Instant le, UtilisateurId par) {
        if (le == null || par == null) {
            throw new IllegalArgumentException("L'auteur et l'instant de l'acte sont obligatoires");
        }
    }

    /**
     * Seul un mois terminé se clôture (RG-ABS-070) : refus {@code MOIS_D_ABSENCES_NON_TERMINE} pour le mois en cours et les mois
     * futurs, {@code aujourdhui} étant la date du jour au fuseau du centre. Aucun jour de grâce. À contrôler avant de verrouiller le
     * mois.
     *
     * @throws MoisDAbsencesNonTermine {@code MOIS_D_ABSENCES_NON_TERMINE}
     */
    public static void exigerTermine(YearMonth mois, LocalDate aujourdhui) {
        if (!mois.isBefore(YearMonth.from(aujourdhui))) {
            throw new MoisDAbsencesNonTermine(mois);
        }
    }

    /**
     * Clôture le mois, une seule fois par ouverture.
     *
     * @param aQualifierRestantes absences encore « à qualifier » du mois (avertissement, tracé au journal)
     * @param enRetard            parmi elles, celles dont le délai de qualification est dépassé
     * @throws MoisDAbsencesDejaClos {@code MOIS_D_ABSENCES_DEJA_CLOS}
     */
    public EntreeDuJournalDuMois cloturer(Instant le, UtilisateurId par, long aQualifierRestantes, long enRetard) {
        exigerUnActe(le, par);
        if (clos) {
            throw new MoisDAbsencesDejaClos(mois);
        }
        clos = true;
        closLe = le;
        closPar = par;
        return EntreeDuJournalDuMois.cloture(centre, mois, le, par, aQualifierRestantes, enRetard);
    }

    /**
     * Rouvre le mois clos ; le motif est obligatoire (rogné, au plus {@value EntreeDuJournalDuMois#MOTIF_MAX} caractères). Rien
     * n'oblige à rouvrir d'abord le mois de facturation ni n'interdit de rouvrir quand il est clos (ADR 0042).
     *
     * @throws MoisDAbsencesNonClos          {@code MOIS_D_ABSENCES_NON_CLOS}, contrôlé avant le motif
     * @throws MotifDeReouvertureObligatoire {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE}
     */
    public EntreeDuJournalDuMois rouvrir(Instant le, UtilisateurId par, String motif) {
        exigerUnActe(le, par);
        if (!clos) {
            throw new MoisDAbsencesNonClos(mois);
        }
        if (motif == null || motif.isBlank()) {
            throw new MotifDeReouvertureObligatoire();
        }
        String motifRetenu = motif.strip();
        if (motifRetenu.length() > EntreeDuJournalDuMois.MOTIF_MAX) {
            throw new MotifDeReouvertureObligatoire(
                    "Le motif de réouverture ne peut pas dépasser " + EntreeDuJournalDuMois.MOTIF_MAX + " caractères");
        }
        clos = false;
        rouvertLe = le;
        rouvertPar = par;
        motifDeReouverture = motifRetenu;
        return EntreeDuJournalDuMois.reouverture(centre, mois, le, par, motifRetenu);
    }

    /**
     * @throws MoisDAbsencesClos {@code MOIS_D_ABSENCES_CLOS} si le mois est clos
     */
    public void exigerOuvert() {
        if (clos) {
            throw new MoisDAbsencesClos(mois);
        }
    }

    public CentreId centre() {
        return centre;
    }

    public YearMonth mois() {
        return mois;
    }

    public boolean estClos() {
        return clos;
    }

    public Instant closLe() {
        return closLe;
    }

    public UtilisateurId closPar() {
        return closPar;
    }

    public Instant rouvertLe() {
        return rouvertLe;
    }

    public UtilisateurId rouvertPar() {
        return rouvertPar;
    }

    public String motifDeReouverture() {
        return motifDeReouverture;
    }
}
