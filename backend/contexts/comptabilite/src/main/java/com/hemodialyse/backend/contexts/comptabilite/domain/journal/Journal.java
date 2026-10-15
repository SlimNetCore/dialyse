package com.hemodialyse.backend.contexts.comptabilite.domain.journal;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;

import java.util.List;

/**
 * Journal comptable d'un centre : code, libellé, et s'il reçoit encore des écritures (RG-CPT-009). Un journal choisi pour une
 * opération ou un modèle de pièce ne se désactive ni ne se supprime ; un journal qui porte des écritures se désactive mais
 * ne se supprime pas. Un centre qui n'a rien paramétré voit les {@linkplain #parDefaut cinq journaux par défaut}.
 */
public final class Journal {

    public static final int LIBELLE_MAX = 100;

    private final CentreId centre;
    private final CodeJournal code;
    private String libelle;
    private boolean actif;

    private Journal(CentreId centre, CodeJournal code, String libelle, boolean actif) {
        if (code == null) throw new IllegalArgumentException("Le code du journal est obligatoire");
        this.centre = centre;
        this.code = code;
        this.libelle = libelleValide(libelle);
        this.actif = actif;
    }

    /**
     * Un journal tel que la base ou les journaux par défaut le décrivent (aucune règle d'usage : il existe déjà).
     */
    public static Journal reconstitue(CentreId centre, CodeJournal code, String libelle, boolean actif) {
        return new Journal(centre, code, libelle, actif);
    }

    /**
     * Ouvre un journal ; refusé s'il est créé désactivé alors qu'une opération ou un modèle le choisit déjà.
     */
    public static Journal ouvrir(CentreId centre, CodeJournal code, String libelle, boolean actif, UsagesDuParametrage usages) {
        Journal journal = new Journal(centre, code, libelle, actif);
        if (!actif) {
            journal.exigerNonUtilise(usages, "désactiver");
        }
        return journal;
    }

    /**
     * Journaux proposés à un centre qui n'a encore rien paramétré (RG-CPT-009).
     */
    public static List<Journal> parDefaut(CentreId centre) {
        return List.of(
                reconstitue(centre, CodeJournal.VE, "Ventes", true),
                reconstitue(centre, CodeJournal.BQ, "Banque", true),
                reconstitue(centre, CodeJournal.CA, "Caisse", true),
                reconstitue(centre, CodeJournal.AC, "Achats", true),
                reconstitue(centre, CodeJournal.ST, "Stocks", true));
    }

    private static String libelleValide(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException("Le libellé du journal est obligatoire");
        }
        String rogne = libelle.trim();
        if (rogne.length() > LIBELLE_MAX) {
            throw new IllegalArgumentException("Le libellé du journal dépasse " + LIBELLE_MAX + " caractères");
        }
        return rogne;
    }

    /**
     * Remplace le libellé et l'état ; la désactivation est refusée si le journal est utilisé.
     */
    public void redefinir(String libelle, boolean actif, UsagesDuParametrage usages) {
        String nouveauLibelle = libelleValide(libelle);
        if (!actif) {
            exigerNonUtilise(usages, "désactiver");
        }
        this.libelle = nouveauLibelle;
        this.actif = actif;
    }

    /**
     * Refuse la suppression d'un journal utilisé (prioritaire) ou qui porte des écritures.
     */
    public void exigerSuppressible(UsagesDuParametrage usages) {
        exigerNonUtilise(usages, "supprimer");
        if (usages.journalAvecEcritures(centre, code)) {
            throw new JournalAvecEcritures(code);
        }
    }

    private void exigerNonUtilise(UsagesDuParametrage usages, String action) {
        if (usages.journalUtilise(centre, code)) {
            throw new JournalUtilise(code, action);
        }
    }

    public CentreId centre() {
        return centre;
    }

    public CodeJournal code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public boolean actif() {
        return actif;
    }
}
