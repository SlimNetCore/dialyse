package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.intervenant.IntervenantVue;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.LectureIntervenants;
import com.hemodialyse.backend.contexts.gmao.application.intervenant.PageDIntervenants;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;

/** Adapter JPA du read model {@link LectureIntervenants} : tri fixe par nom croissant, comme le legacy (KB-GMAO-28). */
public class LectureIntervenantsJpa implements LectureIntervenants {

    private final IntervenantSpringData donnees;

    public LectureIntervenantsJpa(IntervenantSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public Optional<IntervenantVue> parId(IntervenantId id) {
        return donnees.findById(id.valeur()).map(IntervenantMapper::versVue);
    }

    @Override
    public PageDIntervenants duCentre(CentreId centre, int page, int size) {
        Page<IntervenantJpa> resultat = donnees.duCentre(centre.valeur(), PageRequest.of(page, size, Sort.by("nom").ascending()));
        return new PageDIntervenants(resultat.getContent().stream().map(IntervenantMapper::versVue).toList(),
                resultat.getTotalElements(), page, size);
    }
}
