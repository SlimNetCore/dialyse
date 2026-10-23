package com.hemodialyse.backend.contexts.stock.infrastructure.persistence;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * Compteurs de pièces d'un centre ({@code app_settings}, clés {@code SEQ_BC}, {@code SEQ_BR}…) : incrément atomique dans la
 * transaction de la pièce (la ligne est verrouillée par la mise à jour), préfixe par défaut à la première utilisation, numéro sur
 * cinq chiffres. Un refus de la pièce annule l'incrément : aucune référence n'est consommée.
 */
final class SequenceDesPiecesJdbc {

    private final JdbcTemplate jdbc;

    SequenceDesPiecesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    String suivante(UUID centre, String cle, String prefixeParDefaut) {
        if (incrementer(centre, cle) == 0) {
            try {
                jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) VALUES (?, ?, ?, 1)", centre, cle,
                        prefixeParDefaut);
            } catch (DuplicateKeyException concurrent) {
                incrementer(centre, cle);
            }
        }
        return jdbc.queryForObject("SELECT prefixe, dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ?",
                (rs, i) -> (rs.getString("prefixe") == null ? prefixeParDefaut : rs.getString("prefixe"))
                        + String.format("%05d", rs.getLong("dernier_compteur")), centre, cle);
    }

    private int incrementer(UUID centre, String cle) {
        return jdbc.update("UPDATE app_settings SET dernier_compteur = dernier_compteur + 1 WHERE center_id = ? AND cle = ?", centre, cle);
    }
}

