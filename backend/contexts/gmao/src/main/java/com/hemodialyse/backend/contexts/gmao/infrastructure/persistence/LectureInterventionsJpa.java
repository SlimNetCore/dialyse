package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PageDInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;

/** Adapter JPA du read model {@link LectureInterventions} : tri fixe par date de création décroissante, comme le legacy (KB-GMAO-54). */
public class LectureInterventionsJpa implements LectureInterventions {

    private final InterventionSpringData donnees;
    private final InterventionRepositoryJpa repository;

    public LectureInterventionsJpa(InterventionSpringData donnees, InterventionRepositoryJpa repository) {
        this.donnees = donnees;
        this.repository = repository;
    }

    @Override
    public Optional<Intervention> parId(InterventionId id) {
        return donnees.nonSupprimee(id.valeur()).map(repository::charger);
    }

    @Override
    public Optional<Intervention> derniereDeLEquipement(EquipementId equipement) {
        return donnees.parDebutDecroissant(equipement.valeur(), PageRequest.of(0, 1)).stream().findFirst()
                .map(repository::charger);
    }

    @Override
    public PageDInterventions<Intervention> duCentre(CentreId centre, String statut, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        Page<InterventionJpa> resultat = statut == null || statut.isBlank()
                ? donnees.duCentre(centre.valeur(), pageable)
                : donnees.duCentreParStatut(centre.valeur(), statut, pageable);
        return convertir(resultat, page, size);
    }

    @Override
    public PageDInterventions<Intervention> deLEquipement(EquipementId equipement, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        return convertir(donnees.deLEquipement(equipement.valeur(), pageable), page, size);
    }

    private PageDInterventions<Intervention> convertir(Page<InterventionJpa> resultat, int page, int size) {
        return new PageDInterventions<>(resultat.getContent().stream().map(repository::charger).toList(),
                resultat.getTotalElements(), page, size);
    }
}
