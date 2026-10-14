package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.Optional;

/** Consulte un équipement du centre, supprimé logiquement compris (KB-GMAO-34) ; inconnu ou d'un autre centre : absent. */
public final class ConsulterEquipement {

    private final LectureEquipements lecture;

    public ConsulterEquipement(LectureEquipements lecture) {
        this.lecture = lecture;
    }

    public Optional<EquipementVue> executer(EquipementId id, CentreId centre) {
        return lecture.parId(id).filter(e -> e.centreId().equals(centre.valeur()));
    }
}
