package com.hemodialyse.backend.contexts.comptabilite.infrastructure.persistence;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.NumerotationDesDocuments;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Compteurs de documents d'un centre ({@code app_settings}, clés {@code SEQ_BC}, {@code SEQ_BR}, {@code SEQ_ORD}…) : incrément
 * atomique dans la transaction du document (la ligne est verrouillée par la mise à jour), préfixe par défaut à la première
 * utilisation, numéro sur cinq chiffres. Algorithme repris à l'identique du Stock (S-DDL2, ADR 0185).
 */
public final class NumerotationDesDocumentsJdbc implements NumerotationDesDocuments {

    private final JdbcTemplate jdbc;

    public NumerotationDesDocumentsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String suivant(UUID centre, String type, String prefixeParDefaut) {
        if (incrementer(centre, type) == 0) {
            try {
                jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, 1)", centre, type,
                        prefixeParDefaut);
            } catch (DuplicateKeyException concurrent) {
                incrementer(centre, type);
            }
        }
        return jdbc.queryForObject("SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ?",
                (rs, i) -> (rs.getString("prefixe") == null ? prefixeParDefaut : rs.getString("prefixe"))
                        + String.format("%05d", rs.getLong("dernier_compteur")), centre, type);
    }

    private int incrementer(UUID centre, String type) {
        return jdbc.update("UPDATE app_settings SET dernier_compteur = dernier_compteur + 1 WHERE center_id = ? AND cle = ?", centre,
                type);
    }
}
