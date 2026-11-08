package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures publiées de la Séance (ADR 0096) sur la table {@code seances}, lecture seule, mêmes requêtes et même sémantique que les ACL du
 * Stock qu'elles remplacent : le statut {@code FACTUREE} est comparé sans tenir compte de la casse ; la salle et le créneau ne sont lus
 * que dans le centre de la séance (sinon {@code null}). Le code et le libellé de la salle et du créneau viennent des tables de
 * l'Organisation ({@code salle}, {@code position_creneau}), lecture directe inscrite dans la liste blanche de
 * {@code TableDeLOrganisationTest} (jalon O4).
 */
public class LecturesPublieesDesSeancesJdbc implements FacturationDesSeancesPubliee, PlacesDesSeancesPubliees {

    private final JdbcTemplate jdbc;

    public LecturesPublieesDesSeancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean estFacturee(UUID centre, UUID seance) {
        return jdbc.queryForList("SELECT statut FROM seances WHERE id = ? AND center_id = ?", String.class, seance, centre)
                .stream().anyMatch("FACTUREE"::equalsIgnoreCase);
    }

    @Override
    public Optional<PlaceDeSeancePubliee> de(UUID centre, UUID seance) {
        List<UUID[]> ids = jdbc.query("SELECT salle_id, creneau_id FROM seances WHERE id = ? AND center_id = ?",
                (rs, i) -> new UUID[]{rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class)}, seance, centre);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        UUID salle = ids.get(0)[0];
        UUID creneau = ids.get(0)[1];
        String codeSalle = salle == null ? null : jdbc.queryForList("SELECT code FROM salle WHERE id = ? AND center_id = ?",
                String.class, salle, centre).stream().findFirst().orElse(null);
        String[] texte = creneau == null ? null : jdbc.query("SELECT code, libelle FROM position_creneau WHERE id = ? AND center_id = ?",
                (rs, i) -> new String[]{rs.getString("code"), rs.getString("libelle")}, creneau, centre).stream().findFirst().orElse(null);
        return Optional.of(new PlaceDeSeancePubliee(codeSalle, texte == null ? null : texte[0], texte == null ? null : texte[1]));
    }
}
