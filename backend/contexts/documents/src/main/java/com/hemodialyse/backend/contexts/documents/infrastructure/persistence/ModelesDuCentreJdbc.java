package com.hemodialyse.backend.contexts.documents.infrastructure.persistence;

import com.hemodialyse.backend.contexts.documents.application.ModelesDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/** Lecture de {@code modele_document} pour la personnalisation des gabarits : SQL repris de l'ancien {@code findModele}. */
public class ModelesDuCentreJdbc implements ModelesDuCentre {

    private final JdbcTemplate jdbc;

    public ModelesDuCentreJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ModeleDuCentre> duCentre(UUID centre, UUID modele) {
        return jdbc.query("SELECT id, center_id, code, type_document, chemin_jrxml FROM modele_document WHERE id = ? AND center_id = ?",
                (rs, i) -> new ModeleDuCentre(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getString("code"),
                        rs.getString("type_document"), rs.getString("chemin_jrxml")), modele, centre).stream().findFirst();
    }
}
