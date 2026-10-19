package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.PageEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;

import java.time.LocalDate;

/**
 * Consultation paginée des écritures dans le périmètre d'un centre.
 */
public final class ListerLesEcritures {
    private final EcritureComptableRepository ecritures;
    private final UniteDeTravail uniteDeTravail;

    public ListerLesEcritures(EcritureComptableRepository ecritures, UniteDeTravail uniteDeTravail) {
        this.ecritures = ecritures;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PageDe<EcritureVue> executer(CentreId centre, LocalDate du, LocalDate au, CodeJournal journal,
                                        StatutEcriture statut, int page, int taille) {
        return uniteDeTravail.executer(() -> {
            PageEcritures resultat = ecritures.rechercher(centre, du, au, journal, statut, page, taille);
            return new PageDe<>(resultat.items().stream().map(EcritureVue::depuis).toList(), resultat.total(),
                    resultat.page(), resultat.size());
        });
    }
}
