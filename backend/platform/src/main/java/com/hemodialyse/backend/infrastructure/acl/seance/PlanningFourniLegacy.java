package com.hemodialyse.backend.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlanningFourni;
import com.hemodialyse.backend.domain.seance.port.SeancePlanningPort;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournit à la Séance le langage publié {@link PlanningFourni} (ACL vers le planning legacy : jours de dialyse, fermetures du centre,
 * état du patient). Réutilise la lecture existante {@link SeancePlanningPort} ; un patient inconnu du centre devient « vide ».
 */
@Component
public class PlanningFourniLegacy implements PlanningFourni {

    private final SeancePlanningPort planning;

    public PlanningFourniLegacy(SeancePlanningPort planning) {
        this.planning = planning;
    }

    @Override
    public Optional<Situation> situation(UUID centre, UUID patient, LocalDate jour) {
        try {
            var s = planning.situation(CenterId.of(centre), patient, jour);
            return Optional.of(new Situation(s.centreFerme(), s.jourDialyse(), s.enSommeil(), s.etatPatient(), s.dateEvenementEtat(),
                    s.dateAdmission()));
        } catch (BusinessException e) {
            if ("PATIENT_INTROUVABLE".equals(e.getCode())) return Optional.empty();
            throw e;
        }
    }
}
