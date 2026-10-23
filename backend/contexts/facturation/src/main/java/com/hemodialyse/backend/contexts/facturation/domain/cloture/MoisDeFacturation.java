package com.hemodialyse.backend.contexts.facturation.domain.cloture;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Clôture mensuelle de facturation (RG-FAC-040 à 045, ADR 0037) : l'état d'un mois d'un centre, ouvert ou clos. Tant que le mois
 * est clos, la facturation ne peut plus y émettre de facture, ni y exclure ou réinclure une séance, ni y changer un forfait.
 * Clôturer ne demande aucune précondition (l'administrateur décide) ; rouvrir exige un motif. Un mois sans ligne est ouvert.
 * <p>
 * L'agrégat retient la dernière clôture et la dernière réouverture ; chaque acte renvoie son entrée du journal, qui ne se
 * modifie jamais (append-only). Le nom {@code PeriodeDeFacturation} est pris par l'intervalle de dates d'une simulation :
 * l'agrégat du mois s'appelle {@code MoisDeFacturation}.
 */
public final class MoisDeFacturation {

    private final CentreId centre;
    private final YearMonth mois;
    private boolean clos;
    private Instant closLe;
    private UUID closPar;
    private Instant rouvertLe;
    private UUID rouvertPar;
    private String motifDeReouverture;

    private MoisDeFacturation(CentreId centre, YearMonth mois, boolean clos, Instant closLe, UUID closPar, Instant rouvertLe,
                              UUID rouvertPar, String motifDeReouverture) {
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

    /** Un mois qui n'a jamais été clos. */
    public static MoisDeFacturation ouvert(CentreId centre, YearMonth mois) {
        return new MoisDeFacturation(centre, mois, false, null, null, null, null, null);
    }

    /** Un mois tel qu'il est enregistré. */
    public static MoisDeFacturation reconstituer(CentreId centre, YearMonth mois, boolean clos, Instant closLe, UUID closPar,
                                                 Instant rouvertLe, UUID rouvertPar, String motifDeReouverture) {
        return new MoisDeFacturation(centre, mois, clos, closLe, closPar, rouvertLe, rouvertPar, motifDeReouverture);
    }

    /**
     * Clôture le mois, une seule fois par ouverture.
     *
     * @param seancesEligiblesRestantes séances encore à facturer au moment de la clôture (avertissement, tracé au journal)
     */
    public EntreeDuJournal cloturer(Instant le, UUID par, int seancesEligiblesRestantes) {
        exigerUnActe(le, par);
        if (clos) {
            throw new MoisDejaClos(mois);
        }
        clos = true;
        closLe = le;
        closPar = par;
        return EntreeDuJournal.cloture(centre, mois, le, par, seancesEligiblesRestantes);
    }

    /**
     * Seul un mois terminé se clôture (ADR 0044, comme les absences) : refus {@code PERIODE_NON_TERMINEE} pour le mois en cours et les
     * mois futurs, {@code aujourdhui} étant la date du jour au fuseau du centre. Aucun jour de grâce.
     */
    public static void exigerTermine(YearMonth mois, LocalDate aujourdhui) {
        if (!mois.isBefore(YearMonth.from(aujourdhui))) {
            throw new MoisDeFacturationNonTermine(mois);
        }
    }

    /**
     * Le motif de réouverture retenu (sans espaces de bord), ou le refus {@code MOTIF_DE_REOUVERTURE_OBLIGATOIRE} s'il est absent,
     * blanc ou plus long que {@link EntreeDuJournal#MOTIF_MAX} caractères. Exposé pour que le cas d'usage le vérifie avant toute
     * consultation d'un autre contexte (RG-FAC-043 : mois non clos, puis motif, puis période comptable).
     */
    public static String motifDeReouvertureValide(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new MotifDeReouvertureObligatoire();
        }
        String motifRetenu = motif.strip();
        if (motifRetenu.length() > EntreeDuJournal.MOTIF_MAX) {
            throw new MotifDeReouvertureObligatoire(
                    "Le motif de reouverture ne peut pas depasser " + EntreeDuJournal.MOTIF_MAX + " caracteres");
        }
        return motifRetenu;
    }

    /**
     * Rouvre le mois clos ; le motif est obligatoire. La vérification de la période comptable est faite par le cas d'usage.
     */
    public EntreeDuJournal rouvrir(Instant le, UUID par, String motif) {
        exigerUnActe(le, par);
        if (!clos) {
            throw new MoisNonClos(mois);
        }
        String motifRetenu = motifDeReouvertureValide(motif);
        clos = false;
        rouvertLe = le;
        rouvertPar = par;
        motifDeReouverture = motifRetenu;
        return EntreeDuJournal.reouverture(centre, mois, le, par, motifRetenu);
    }

    /** Refus {@code PERIODE_DE_FACTURATION_CLOTUREE} si le mois est clos. */
    public void exigerOuvert() {
        if (clos) {
            throw new MoisDeFacturationClos(mois);
        }
    }

    private static void exigerUnActe(Instant le, UUID par) {
        if (le == null || par == null) {
            throw new IllegalArgumentException("L'auteur et l'instant de l'acte sont obligatoires");
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

    public UUID closPar() {
        return closPar;
    }

    public Instant rouvertLe() {
        return rouvertLe;
    }

    public UUID rouvertPar() {
        return rouvertPar;
    }

    public String motifDeReouverture() {
        return motifDeReouverture;
    }
}
