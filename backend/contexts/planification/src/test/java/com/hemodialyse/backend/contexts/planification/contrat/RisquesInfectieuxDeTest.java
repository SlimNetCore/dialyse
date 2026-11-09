package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.RisquesInfectieuxFournis;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Double de TEST du port fourni {@link RisquesInfectieuxFournis} : en production, la plateforme le fournit depuis le langage publié du
 * Dossier médical ({@code PatientsARisqueInfectieuxJdbc}, contrat mémoire / H2 / PG16 dans ce contexte-là). Ici la même requête (RG-PLN-012)
 * est jouée sur la table posée par les contrats de la Planification (DM9, ADR 0101).
 */
public final class RisquesInfectieuxDeTest implements RisquesInfectieuxFournis {

    private final JdbcTemplate jdbc;

    public RisquesInfectieuxDeTest(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> patientsARisque(UUID centre) {
        return new HashSet<>(jdbc.query("SELECT DISTINCT s.patient_id FROM serologies_patient s "
                        + "WHERE s.center_id = ? AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') AND s.resultat = 'POSITIF' "
                        + "AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = s.center_id AND n.patient_id = s.patient_id "
                        + "AND n.marqueur = s.marqueur AND n.resultat IN ('POSITIF', 'NEGATIF') AND (n.date_prelevement > s.date_prelevement "
                        + "OR (n.date_prelevement = s.date_prelevement AND n.created_at > s.created_at)))",
                (rs, i) -> rs.getObject("patient_id", UUID.class), centre));
    }
}
