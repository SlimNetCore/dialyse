package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyorganisation;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.SallesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * ACL vers les salles d'Organisation (encore dans le legacy, ADR 0053) : {@code salle.isolement} vaut {@code 'OUI'} ou
 * {@code 'NON'}. Même SQL que le legacy ; retirée quand Organisation sera un contexte.
 */
public class SallesDuCentreJdbc implements SallesDuCentre {

    static final String OUI = "OUI";
    static final String NON = "NON";

    private final JdbcTemplate jdbc;

    public SallesDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<SalleId> toutes(CentreId centre) {
        return new HashSet<>(jdbc.query("SELECT id FROM salle WHERE center_id = ?",
                (rs, i) -> new SalleId(rs.getObject("id", UUID.class)), centre.valeur()));
    }

    @Override
    public Set<SalleId> sallesDIsolement(CentreId centre) {
        return new HashSet<>(jdbc.query("SELECT id FROM salle WHERE center_id = ? AND isolement = ?",
                (rs, i) -> new SalleId(rs.getObject("id", UUID.class)), centre.valeur(), OUI));
    }

    @Override
    public void marquerIsolement(CentreId centre, Set<SalleId> sallesDIsolement) {
        jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ?", NON, centre.valeur());
        for (SalleId salle : sallesDIsolement) {
            jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ? AND id = ?", OUI, centre.valeur(), salle.valeur());
        }
    }
}
