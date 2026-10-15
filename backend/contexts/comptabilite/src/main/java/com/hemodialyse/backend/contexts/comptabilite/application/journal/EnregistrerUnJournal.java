package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

/**
 * Crée un journal ou remplace le libellé et l'état de celui qui porte ce code (normalisé en majuscules). La première
 * modification enregistre les journaux par défaut comme ceux du centre ; un refus annule cet enregistrement.
 */
public final class EnregistrerUnJournal {

    private final JournalRepository journaux;
    private final JournauxDuCentreVus vus;
    private final UsagesDuParametrage usages;
    private final UniteDeTravail uniteDeTravail;

    public EnregistrerUnJournal(JournalRepository journaux, JournauxDuCentreVus vus, UsagesDuParametrage usages,
                                UniteDeTravail uniteDeTravail) {
        this.journaux = journaux;
        this.vus = vus;
        this.usages = usages;
        this.uniteDeTravail = uniteDeTravail;
    }

    public JournalVue executer(CentreId centre, String code, String libelle, boolean actif) {
        CodeJournal codeVise = CodeJournal.de(code);
        return uniteDeTravail.executer(() -> {
            vus.materialiser(centre);
            Journal journal = journaux.trouver(centre, codeVise).orElse(null);
            if (journal == null) {
                journal = Journal.ouvrir(centre, codeVise, libelle, actif, usages);
            } else {
                journal.redefinir(libelle, actif, usages);
            }
            journaux.enregistrer(journal);
            return JournalVue.depuis(journal);
        });
    }
}
