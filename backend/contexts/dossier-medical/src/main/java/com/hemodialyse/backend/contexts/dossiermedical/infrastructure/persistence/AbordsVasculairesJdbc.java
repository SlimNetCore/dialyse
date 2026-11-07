package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.horodatage;
import static com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence.VerrousJdbc.instant;

/**
 * Abords vasculaires sur la table réelle {@code abords_vasculaires} (aucune colonne ni contrainte ajoutée ; la Planification la lit en SQL
 * pour la compétence CATHETER : le format des lignes ne change donc pas). Toute lecture et toute écriture portent sur (identifiant,
 * patient, centre) : un abord n'est jamais réaffecté (KB-MED-62). Verrou : {@code SELECT ... FOR UPDATE} de la fiche, gardé jusqu'à la fin
 * de la transaction ; un verrou non obtenu dans le délai (SQLSTATE 55P03) devient {@link EcritureConcurrenteSurUnAbordVasculaire}. Aucune
 * ligne n'est illisible (type libre, actif non nul).
 */
public class AbordsVasculairesJdbc implements AbordsVasculaires {
    private static final String COLONNES = "id, center_id, patient_id, type_abord, cote, localisation, date_creation, date_fin, actif, "
            + "complications, created_at";

    private final JdbcTemplate jdbc;

    public AbordsVasculairesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(AbordVasculaire a) {
        jdbc.update("INSERT INTO abords_vasculaires (" + COLONNES + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)", a.id(), a.centre().valeur(),
                a.patient().valeur(), a.type(), a.cote(), a.localisation(), a.creation(), a.fin(), a.actif(), a.complications(),
                horodatage(a.enregistreLe()));
    }

    @Override
    public void enregistrerLeRemplacement(AbordVasculaire a) {
        jdbc.update("UPDATE abords_vasculaires SET type_abord = ?, cote = ?, localisation = ?, date_creation = ?, date_fin = ?, actif = ?, "
                        + "complications = ?, created_at = ? WHERE id = ? AND center_id = ? AND patient_id = ?", a.type(), a.cote(),
                a.localisation(), a.creation(), a.fin(), a.actif(), a.complications(), horodatage(a.enregistreLe()), a.id(), a.centre().valeur(),
                a.patient().valeur());
    }

    @Override
    public Optional<AbordVasculaire> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        return VerrousJdbc.verrouille(() -> jdbc.query("SELECT " + COLONNES
                        + " FROM abords_vasculaires WHERE id = ? AND patient_id = ? AND center_id = ? FOR UPDATE", (rs, i) -> abord(rs), id,
                patient.valeur(), centre.valeur()).stream().findFirst(), EcritureConcurrenteSurUnAbordVasculaire::new);
    }

    @Override
    public boolean supprimer(CentreId centre, PatientId patient, UUID id) {
        return jdbc.update("DELETE FROM abords_vasculaires WHERE id = ? AND patient_id = ? AND center_id = ?", id, patient.valeur(),
                centre.valeur()) > 0;
    }

    @Override
    public PageDeFiches<AbordVasculaire> duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM abords_vasculaires WHERE center_id = ? AND patient_id = ?", Long.class,
                centre.valeur(), patient.valeur());
        List<AbordVasculaire> items = jdbc.query("SELECT " + COLONNES + " FROM abords_vasculaires WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY date_creation DESC NULLS LAST, created_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> abord(rs),
                centre.valeur(), patient.valeur(), taille, (long) page * taille);
        return new PageDeFiches<>(items, total == null ? 0 : total, page, taille);
    }

    private static AbordVasculaire abord(ResultSet rs) throws SQLException {
        return AbordVasculaire.reconstituer(rs.getObject("id", UUID.class), new PatientId(rs.getObject("patient_id", UUID.class)),
                new CentreId(rs.getObject("center_id", UUID.class)), rs.getString("type_abord"), rs.getString("cote"),
                rs.getString("localisation"), rs.getObject("date_creation", LocalDate.class), rs.getObject("date_fin", LocalDate.class),
                rs.getBoolean("actif"), rs.getString("complications"), instant(rs, "created_at"));
    }
}
