package com.hemodialyse.backend.infrastructure.reporting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Garantit que chaque centre dispose d'un modèle de document pour chaque document du
 * {@link ModeleDocumentCatalog} : au démarrage (tous les centres) et à la première impression (centre créé depuis).
 * Idempotent : un modèle existant (même code ou même type) n'est jamais modifié — un modèle désactivé par le
 * centre reste désactivé.
 */
@Component
public class ModeleDocumentProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeleDocumentProvisioner.class);

    private final JdbcTemplate jdbc;

    public ModeleDocumentProvisioner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<UUID> centers = jdbc.queryForList("SELECT id FROM centers", UUID.class);
            int created = 0;
            for (UUID center : centers) {
                for (ModeleDocumentCatalog.Entry entry : ModeleDocumentCatalog.entries()) {
                    if (ensure(center, entry)) created++;
                }
            }
            if (created > 0) log.info("Modèles de documents provisionnés : {} ({} centre(s))", created, centers.size());
        } catch (DataAccessException e) {
            // Base non initialisée (tests, premier démarrage) : le provisionnement se fera à la première impression.
            log.warn("Provisionnement des modèles de documents différé : {}", e.getMessage());
        }
    }

    /**
     * Crée le modèle du centre pour ce document s'il n'existe pas ; {@code true} si créé.
     */
    public boolean ensure(UUID centerId, ModeleDocumentCatalog.Entry entry) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND (UPPER(type_document) = ? OR UPPER(code) = ?)",
                Integer.class, centerId, entry.type(), entry.type());
        if (existing != null && existing > 0) return false;
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, "
                        + "format_impression, description, active) VALUES (?, ?, ?, ?, ?, ?, 'PDF', ?, TRUE)",
                UUID.randomUUID(), centerId, entry.type(), entry.libelle(), entry.type(), entry.cheminJrxml(),
                entry.description());
        return true;
    }
}

