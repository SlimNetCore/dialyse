package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.equipement.PageDEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

/** Adapter JPA du read model {@link LectureEquipements} : tri fixe par date de création décroissante, comme le legacy. */
public class LectureEquipementsJpa implements LectureEquipements {

    private final EquipementSpringData donnees;

    public LectureEquipementsJpa(EquipementSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public Optional<EquipementVue> parId(EquipementId id) {
        return donnees.findById(id.valeur()).map(EquipementMapper::versVue);
    }

    @Override
    public List<EquipementVue> parcDuCentre(CentreId centre) {
        return donnees.parcDuCentre(centre.valeur()).stream().map(EquipementMapper::versVue).toList();
    }

    @Override
    public PageDEquipements duCentre(CentreId centre, String statut, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateCreation").descending());
        Page<EquipementJpa> resultat = (statut == null || statut.isBlank())
                ? donnees.duCentre(centre.valeur(), pageable)
                : donnees.duCentreParStatut(centre.valeur(), statut, pageable);
        return new PageDEquipements(resultat.getContent().stream().map(EquipementMapper::versVue).toList(),
                resultat.getTotalElements(), page, size);
    }
}
