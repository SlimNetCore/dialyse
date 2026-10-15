package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.Journal;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.JournalRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JPA des journaux paramétrés par un centre ({@code journaux_comptables}). Chaque requête est bornée au centre.
 */
public class JournalRepositoryJpa implements JournalRepository {

    private final JournalComptableSpringData donnees;

    public JournalRepositoryJpa(JournalComptableSpringData donnees) {
        this.donnees = donnees;
    }

    private static Journal versLeDomaine(JournalComptableJpa ligne) {
        return Journal.reconstitue(new CentreId(ligne.getCenterId()), CodeJournal.de(ligne.getCode()), ligne.getLibelle(),
                ligne.isActif());
    }

    @Override
    public List<Journal> duCentre(CentreId centre) {
        return donnees.findByCenterIdOrderByCodeAsc(centre.valeur()).stream().map(JournalRepositoryJpa::versLeDomaine).toList();
    }

    @Override
    public Optional<Journal> trouver(CentreId centre, CodeJournal code) {
        return donnees.findByCenterIdAndCode(centre.valeur(), code.valeur()).map(JournalRepositoryJpa::versLeDomaine);
    }

    @Override
    public void enregistrer(Journal journal) {
        JournalComptableJpa ligne = donnees.findByCenterIdAndCode(journal.centre().valeur(), journal.code().valeur())
                .orElseGet(() -> new JournalComptableJpa(UUID.randomUUID(), journal.centre().valeur(), journal.code().valeur()));
        ligne.setLibelle(journal.libelle());
        ligne.setActif(journal.actif());
        donnees.saveAndFlush(ligne);
    }

    @Override
    public void supprimer(CentreId centre, CodeJournal code) {
        donnees.deleteByCenterIdAndCode(centre.valeur(), code.valeur());
    }
}
