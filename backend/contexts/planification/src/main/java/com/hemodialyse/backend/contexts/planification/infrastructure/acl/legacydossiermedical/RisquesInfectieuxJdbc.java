package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacydossiermedical;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers le Dossier médical (table {@code serologies_patient}, encore dans le legacy) : un patient est à risque infectieux (RG-PLN-012)
 * si, pour au moins un marqueur AgHBs, anti-VHC, ARN du VHC ou VIH, sa <b>dernière</b> sérologie est positive (départage : date de
 * prélèvement puis date de saisie). Même SQL que le legacy ; borné au centre.
 */
public class RisquesInfectieuxJdbc implements RisquesInfectieux {

    private static final String PATIENTS_A_RISQUE =
            "SELECT DISTINCT s.patient_id FROM serologies_patient s "
                    + "WHERE s.center_id = ? AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') "
                    + "AND s.resultat = 'POSITIF' "
                    + "AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = s.center_id "
                    + "AND n.patient_id = s.patient_id AND n.marqueur = s.marqueur "
                    + "AND (n.date_prelevement > s.date_prelevement "
                    + "OR (n.date_prelevement = s.date_prelevement AND n.created_at > s.created_at)))";

    private final JdbcTemplate jdbc;

    public RisquesInfectieuxJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Les patients à risque du centre (lecture groupée, utilisée par le chargement des places). */
    public static Set<UUID> patientsARisque(JdbcTemplate jdbc, UUID centre) {
        return new HashSet<>(jdbc.query(PATIENTS_A_RISQUE, (rs, i) -> rs.getObject("patient_id", UUID.class), centre));
    }

    @Override
    public CategorieDIsolement de(CentreId centre, PatientId patient) {
        return CategorieDIsolement.deduite(patientsARisque(jdbc, centre.valeur()).contains(patient.valeur()));
    }
}
