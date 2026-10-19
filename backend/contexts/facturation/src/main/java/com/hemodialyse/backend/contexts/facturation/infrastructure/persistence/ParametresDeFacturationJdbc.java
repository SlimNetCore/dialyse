package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * Adapter JDBC des paramètres de facturation : table {@code facturation_settings} (clé {@code center_id}). L'écriture est
 * une mise à jour, puis une insertion si le centre n'avait rien : SQL standard, valable sur H2 comme sur PostgreSQL
 * (KB-FAC-07 corrigé : l'ancien {@code MERGE INTO … KEY (center_id)} n'existe que sur H2). La colonne héritée
 * {@code tva_rate} n'est ni lue ni écrite.
 */
public class ParametresDeFacturationJdbc implements ParametresDeFacturationRepository {

    private final JdbcTemplate jdbc;

    public ParametresDeFacturationJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ParametresDeFacturation> duCentre(CentreId centre) {
        return jdbc.query("SELECT code_format, regroupement_multi_forfait, updated_at FROM facturation_settings "
                        + "WHERE center_id = ?",
                (rs, ligne) -> {
                    Timestamp misAJourLe = rs.getTimestamp("updated_at");
                    return new ParametresDeFacturation(new ModeleDeNumerotation(rs.getString("code_format")),
                            rs.getBoolean("regroupement_multi_forfait"), misAJourLe == null ? null : misAJourLe.toInstant());
                },
                centre.valeur()).stream().findFirst();
    }

    @Override
    public void enregistrer(CentreId centre, ParametresDeFacturation parametres, String auteur) {
        Timestamp misAJourLe = parametres.misAJourLe() == null ? null : Timestamp.from(parametres.misAJourLe());
        int modifiees = jdbc.update("UPDATE facturation_settings SET code_format = ?, regroupement_multi_forfait = ?, "
                        + "updated_at = ?, updated_by = ? WHERE center_id = ?",
                parametres.modele().valeur(), parametres.regroupementMultiForfait(), misAJourLe, auteur, centre.valeur());
        if (modifiees == 0) {
            jdbc.update("INSERT INTO facturation_settings (center_id, code_format, regroupement_multi_forfait, updated_at, "
                            + "updated_by) VALUES (?, ?, ?, ?, ?)",
                    centre.valeur(), parametres.modele().valeur(), parametres.regroupementMultiForfait(), misAJourLe, auteur);
        }
    }
}
