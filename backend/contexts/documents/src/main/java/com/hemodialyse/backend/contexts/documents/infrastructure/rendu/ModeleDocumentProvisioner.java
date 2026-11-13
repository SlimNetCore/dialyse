package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.CatalogueDesTypesLivres;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Garantit que chaque centre dispose d'un modèle de document pour chaque document du
 * {@link CatalogueDesTypesLivres} (les types que les contextes propriétaires déclarent, ADR 0166) : au démarrage (tous les centres) et à la première impression (centre créé depuis).
 * Idempotent : un modèle existant (même code ou même type) n'est jamais modifié — un modèle désactivé par le
 * centre reste désactivé.
 */
public class ModeleDocumentProvisioner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModeleDocumentProvisioner.class);

    private final JdbcTemplate jdbc;
    private final AnnuaireDesCentres annuaireDesCentres;
    private final CatalogueDesTypesLivres catalogue;

    /** Les centres à provisionner sont lus par le langage publié d'Organisation (O4 lot E, ADR 0111) ; les modèles par centre sont lus et écrits ici jusqu'à DOC3 (le provisionnement est inséparable du rendu par type). */
    public ModeleDocumentProvisioner(JdbcTemplate jdbc, AnnuaireDesCentres annuaireDesCentres, CatalogueDesTypesLivres catalogue) {
        this.catalogue = catalogue;
        this.jdbc = jdbc;
        this.annuaireDesCentres = annuaireDesCentres;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<UUID> centers = annuaireDesCentres.tous();
            int created = 0;
            for (UUID center : centers) {
                for (TypeDeDocumentDeclare entry : catalogue.types()) {
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
    public boolean ensure(UUID centerId, TypeDeDocumentDeclare entry) {
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM modele_document WHERE center_id = ? AND (UPPER(type_document) = ? OR UPPER(code) = ?)",
                Integer.class, centerId, entry.code(), entry.code());
        if (existing != null && existing > 0) return false;
        jdbc.update("INSERT INTO modele_document (id, center_id, code, libelle, type_document, chemin_jrxml, "
                        + "format_impression, description, active) VALUES (?, ?, ?, ?, ?, ?, 'PDF', ?, TRUE)",
                UUID.randomUUID(), centerId, entry.code(), entry.libelle(), entry.code(), entry.gabaritParDefaut(),
                entry.description());
        return true;
    }
}

