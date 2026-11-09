package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.RisquesInfectieuxPublies;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Patients à risque infectieux du centre sur la table réelle {@code serologies_patient} (DM9, ADR 0101). SQL repris MOT POUR MOT de
 * {@code contexts/planification/.../acl/legacydossiermedical/RisquesInfectieuxJdbc.java:21-28} (retiré) : marqueurs AG_HBS, AC_VHC, ARN_VHC,
 * VIH_AC en casse stricte, résultat {@code POSITIF}, aucune sérologie du même marqueur et du même patient AU CENTRE plus récente (prélèvement
 * puis saisie), ne retenant que les résultats conclusifs POSITIF/NEGATIF (KB-MED-106 CORRIGÉ le 2026-11-09, ADR 0113 : un DOUTEUX ou EN_COURS plus
 * récent n'efface plus un positif). Forme Java de la règle : {@code RisqueInfectieux} (domaine) ; KB-MED-107 et 108 reproduits.
 */
public class PatientsARisqueInfectieuxJdbc implements RisquesInfectieuxPublies {

    private static final String PATIENTS_A_RISQUE =
            "SELECT DISTINCT s.patient_id FROM serologies_patient s "
                    + "WHERE s.center_id = ? AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') "
                    + "AND s.resultat = 'POSITIF' "
                    + "AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = s.center_id "
                    + "AND n.patient_id = s.patient_id AND n.marqueur = s.marqueur "
                    + "AND n.resultat IN ('POSITIF', 'NEGATIF') "
                    + "AND (n.date_prelevement > s.date_prelevement "
                    + "OR (n.date_prelevement = s.date_prelevement AND n.created_at > s.created_at)))";

    private final JdbcTemplate jdbc;

    public PatientsARisqueInfectieuxJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> patientsARisque(UUID centre) {
        return new HashSet<>(jdbc.query(PATIENTS_A_RISQUE, (rs, i) -> rs.getObject("patient_id", UUID.class), centre));
    }
}
