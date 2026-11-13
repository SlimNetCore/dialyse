package com.hemodialyse.backend.contexts.documents.infrastructure.persistence;

import com.hemodialyse.backend.contexts.documents.application.GabaritPersonnaliseDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersions;
import com.hemodialyse.backend.contexts.documents.domain.HistoriqueDesVersionsRepository;
import com.hemodialyse.backend.contexts.documents.domain.VersionDeGabarit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JDBC de l'historique des versions de gabarit (table {@code modele_document_version}, DDL de {@code schema.sql} = Flyway V1).
 * Remplace l'entité JPA et le dépôt Spring Data de la plateforme : mêmes colonnes, mêmes requêtes (modèle + centre). Il sert aussi le
 * moteur de rendu ({@link GabaritPersonnaliseDuCentre}) : la version en vigueur et son contenu.
 */
public class HistoriqueDesVersionsJdbc implements HistoriqueDesVersionsRepository, GabaritPersonnaliseDuCentre {

    private static final RowMapper<VersionDeGabarit> VERSION = (rs, i) -> new VersionDeGabarit(rs.getObject("id", UUID.class),
            rs.getInt("version"), rs.getBoolean("actif"), rs.getString("sha256"), rs.getInt("taille_octets"), rs.getString("commentaire"),
            rs.getString("uploaded_by"), rs.getTimestamp("uploaded_at").toInstant());

    private final JdbcTemplate jdbc;

    public HistoriqueDesVersionsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public HistoriqueDesVersions historique(UUID centre, UUID modele) {
        List<VersionDeGabarit> versions = jdbc.query("SELECT id, version, actif, sha256, taille_octets, commentaire, uploaded_by, uploaded_at "
                + "FROM modele_document_version WHERE modele_id = ? AND center_id = ? ORDER BY version", VERSION, modele, centre);
        return HistoriqueDesVersions.de(centre, modele, versions);
    }

    @Override
    public void enregistrer(HistoriqueDesVersions historique) {
        UUID enAttente = historique.depotEnAttente().map(d -> d.version().id()).orElse(null);
        for (VersionDeGabarit v : historique.versions()) {
            if (v.id().equals(enAttente)) {
                continue;
            }
            jdbc.update("UPDATE modele_document_version SET actif = ? WHERE id = ? AND actif <> ?", v.active(), v.id(), v.active());
        }
        historique.depotEnAttente().ifPresent(depot -> {
            VersionDeGabarit v = depot.version();
            jdbc.update("INSERT INTO modele_document_version (id, modele_id, center_id, version, contenu, sha256, taille_octets, "
                            + "commentaire, uploaded_by, uploaded_at, actif) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    v.id(), historique.modele(), historique.centre(), v.numero(), depot.contenu(), v.empreinte(), v.tailleEnOctets(),
                    v.commentaire(), v.deposePar(), Timestamp.from(v.deposeLe()), v.active());
        });
    }

    @Override
    public Optional<String> contenuDe(UUID centre, UUID modele, int numero) {
        return jdbc.queryForList("SELECT contenu FROM modele_document_version WHERE modele_id = ? AND center_id = ? AND version = ?",
                String.class, modele, centre, numero).stream().findFirst();
    }

    @Override
    public Optional<String> contenuEnVigueur(UUID centre, UUID modele) {
        return actif(centre, modele).map(Gabarit::contenu);
    }

    @Override
    public Optional<Gabarit> actif(UUID centre, UUID modele) {
        return jdbc.query("SELECT version, contenu FROM modele_document_version WHERE modele_id = ? AND center_id = ? AND actif = TRUE "
                        + "ORDER BY version DESC", (rs, i) -> new Gabarit(rs.getInt("version"), rs.getString("contenu")), modele, centre)
                .stream().findFirst();
    }

    @Override
    public void retirerTout(UUID modele) {
        jdbc.update("DELETE FROM modele_document_version WHERE modele_id = ?", modele);
    }
}
