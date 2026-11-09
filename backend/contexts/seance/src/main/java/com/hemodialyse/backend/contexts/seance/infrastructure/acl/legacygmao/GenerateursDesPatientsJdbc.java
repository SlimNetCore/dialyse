package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacygmao;

import com.hemodialyse.backend.contexts.seance.application.GenerateursDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

/**
 * Générateur du patient (SE7f, ADR 0100) : requête reprise MOT POUR MOT de {@code GenerateursFournisLegacy} (plateforme, retiré). Le
 * générateur est un équipement de la GMAO (source de vérité unique) : code, fabricant et statut, bornés au centre, hors équipement
 * supprimé. Lecture transitoire de {@code gmao_equipements}, inscrite dans {@code TableDesEquipementsDeLaGmaoTest} (le langage publié de
 * la GMAO ne porte pas le fabricant).
 */
public class GenerateursDesPatientsJdbc implements GenerateursDesPatients {

    private final JdbcTemplate jdbc;

    public GenerateursDesPatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Generateur> duCentre(CentreId centre, UUID equipement) {
        return jdbc.query("SELECT id, code, fabricant, statut FROM gmao_equipements WHERE centre_id = ? AND id = ? AND deleted_at IS NULL",
                (rs, i) -> new Generateur(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("fabricant"),
                        rs.getString("statut")),
                centre.valeur(), equipement).stream().findFirst();
    }
}
