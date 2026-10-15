package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.UsagesDuParametrage;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.plan.NumeroDeCompte;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Double en mémoire de {@link UsagesDuParametrage} : les tests déclarent ce qui est utilisé ou porte des écritures.
 */
public class UsagesEnMemoire implements UsagesDuParametrage {

    private final Set<NumeroDeCompte> mapping = new LinkedHashSet<>();
    private final Set<NumeroDeCompte> payeursOuModeles = new HashSet<>();
    private final Set<NumeroDeCompte> articles = new HashSet<>();
    private final Set<NumeroDeCompte> ecritures = new HashSet<>();
    private final Set<CodeJournal> journauxChoisis = new HashSet<>();
    private final Set<CodeJournal> journauxAvecEcritures = new HashSet<>();

    public UsagesEnMemoire mapping(String... comptes) {
        for (String c : comptes) mapping.add(NumeroDeCompte.de(c));
        return this;
    }

    public UsagesEnMemoire utiliseParUnPayeurOuUnModele(String compte) {
        payeursOuModeles.add(NumeroDeCompte.de(compte));
        return this;
    }

    public UsagesEnMemoire rattacheAUnArticle(String compte) {
        articles.add(NumeroDeCompte.de(compte));
        return this;
    }

    public UsagesEnMemoire avecEcritures(String compte) {
        ecritures.add(NumeroDeCompte.de(compte));
        return this;
    }

    public UsagesEnMemoire journalChoisi(String code) {
        journauxChoisis.add(CodeJournal.de(code));
        return this;
    }

    public UsagesEnMemoire journalAvecEcritures(String code) {
        journauxAvecEcritures.add(CodeJournal.de(code));
        return this;
    }

    @Override
    public Set<NumeroDeCompte> comptesDuParametrage(CentreId centre) {
        return Set.copyOf(mapping);
    }

    @Override
    public boolean compteUtilise(CentreId centre, NumeroDeCompte compte) {
        return mapping.contains(compte) || payeursOuModeles.contains(compte);
    }

    @Override
    public boolean compteRattacheAUnArticle(CentreId centre, NumeroDeCompte compte) {
        return articles.contains(compte);
    }

    @Override
    public boolean compteAvecEcritures(CentreId centre, NumeroDeCompte compte) {
        return ecritures.contains(compte);
    }

    @Override
    public boolean journalUtilise(CentreId centre, CodeJournal journal) {
        return journauxChoisis.contains(journal);
    }

    @Override
    public boolean journalAvecEcritures(CentreId centre, CodeJournal journal) {
        return journauxAvecEcritures.contains(journal);
    }
}
