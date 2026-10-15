package com.hemodialyse.backend.contexts.comptabilite.application.journal;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalIntrouvable;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

/**
 * Supprime physiquement un journal : introuvable, puis utilisé, puis porteur d'écritures (dans cet ordre de priorité).
 */
public final class SupprimerUnJournal {

    private final JournalRepository journaux;
    private final JournauxDuCentreVus vus;
    private final UsagesDuParametrage usages;
    private final UniteDeTravail uniteDeTravail;

    public SupprimerUnJournal(JournalRepository journaux, JournauxDuCentreVus vus, UsagesDuParametrage usages,
                              UniteDeTravail uniteDeTravail) {
        this.journaux = journaux;
        this.vus = vus;
        this.usages = usages;
        this.uniteDeTravail = uniteDeTravail;
    }

    public void executer(CentreId centre, String code) {
        CodeJournal codeVise = CodeJournal.de(code);
        uniteDeTravail.executer(() -> {
            vus.materialiser(centre);
            Journal journal = journaux.trouver(centre, codeVise).orElseThrow(() -> new JournalIntrouvable(codeVise));
            journal.exigerSuppressible(usages);
            journaux.supprimer(centre, codeVise);
            return null;
        });
    }
}
