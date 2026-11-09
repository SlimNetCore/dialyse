package com.hemodialyse.backend.contexts.organisation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DefinirLesSallesDIsolement;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;
import java.util.UUID;

/**
 * Commande d'isolement des salles. SQL repris à l'identique de {@code SallesDuCentreJdbc.marquerIsolement} (Planification, retiré au lot D
 * de O4, ADR 0110) : {@code salle.isolement} vaut {@code 'OUI'} ou {@code 'NON'}. Aucune transaction propre (ni {@code @Transactional}, ni
 * {@code REQUIRES_NEW}) : les instructions utilisent le {@code JdbcTemplate} partagé, donc la transaction de l'appelant.
 */
public class IsolementDesSallesJdbc implements DefinirLesSallesDIsolement {

    static final String OUI = "OUI";
    static final String NON = "NON";

    private final JdbcTemplate jdbc;

    public IsolementDesSallesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void definir(UUID centre, Set<UUID> sallesDIsolement) {
        jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ?", NON, centre);
        for (UUID salle : sallesDIsolement) {
            jdbc.update("UPDATE salle SET isolement = ? WHERE center_id = ? AND id = ?", OUI, centre, salle);
        }
    }
}
