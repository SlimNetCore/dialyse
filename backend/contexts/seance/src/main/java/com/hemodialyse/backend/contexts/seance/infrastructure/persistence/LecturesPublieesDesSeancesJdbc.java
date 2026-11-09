package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation.LibelleDeCreneau;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.FacturationDesSeancesPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lectures publiées de la Séance (ADR 0096) sur la table {@code seances}, lecture seule, mêmes requêtes et même sémantique que les ACL du
 * Stock qu'elles remplacent : le statut {@code FACTUREE} est comparé sans tenir compte de la casse ; la salle et le créneau ne sont lus
 * que dans le centre de la séance (sinon {@code null}). Le code de la salle et le code et le libellé du créneau viennent du langage
 * publié d'Organisation ({@link LibellesDeLOrganisation}, O4 lot C, ADR 0109), bornés au centre de la séance : plus aucun SQL sur les
 * tables de l'Organisation.
 */
public class LecturesPublieesDesSeancesJdbc implements FacturationDesSeancesPubliee, PlacesDesSeancesPubliees {

    private final JdbcTemplate jdbc;
    private final LibellesDeLOrganisation libelles;

    public LecturesPublieesDesSeancesJdbc(JdbcTemplate jdbc, LibellesDeLOrganisation libelles) {
        this.jdbc = jdbc;
        this.libelles = libelles;
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
        String codeSalle = salle == null ? null : libelles.codesDesSalles(centre, List.of(salle)).get(salle);
        LibelleDeCreneau texte = creneau == null ? null : libelles.creneauxDuCentre(centre, List.of(creneau)).get(creneau);
        return Optional.of(new PlaceDeSeancePubliee(codeSalle, texte == null ? null : texte.code(), texte == null ? null : texte.libelle()));
    }
}
