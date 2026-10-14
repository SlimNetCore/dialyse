package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.intervenant.Intervenant;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantRepository;

import java.util.Optional;

/** Adapter JPA du port {@link IntervenantRepository} . */
public class IntervenantRepositoryJpa implements IntervenantRepository {

    private final IntervenantSpringData donnees;

    public IntervenantRepositoryJpa(IntervenantSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public void enregistrer(Intervenant intervenant) {
        IntervenantJpa entite = donnees.findById(intervenant.id().valeur())
                .orElseGet(() -> new IntervenantJpa(intervenant.id().valeur()));
        IntervenantMapper.recopier(intervenant, entite);
        donnees.saveAndFlush(entite);
    }

    @Override
    public Optional<Intervenant> trouverParId(IntervenantId id) {
        return donnees.findById(id.valeur()).map(IntervenantMapper::versDomaine);
    }
}
