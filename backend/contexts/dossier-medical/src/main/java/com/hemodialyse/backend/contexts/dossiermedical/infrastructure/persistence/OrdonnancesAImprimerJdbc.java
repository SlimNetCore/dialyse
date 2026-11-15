package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les ordonnances à imprimer sur les tables réelles {@code ordonnances} et {@code lignes_ordonnance} (DOC5d, ADR 0196) : la partie de
 * l'ancienne requête du gabarit qui appartient au Dossier médical, reprise telle quelle. Les lignes sont lues SANS ordre explicite, comme
 * l'ancienne jointure (l'ordre où la base les rend, observé par le filet de rendu) : un {@code ORDER BY} changerait l'ordre imprimé.
 */
public class OrdonnancesAImprimerJdbc implements OrdonnancesAImprimer {

    private final JdbcTemplate jdbc;

    public OrdonnancesAImprimerJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OrdonnanceImprimable> trouver(CentreId centre, UUID ordonnance) {
        record Entete(UUID patient, String numero, LocalDate date, String medecin) {
        }
        List<Entete> entetes = jdbc.query("SELECT patient_id, numero, date_prescription, medecin_id FROM ordonnances "
                        + "WHERE id = ? AND center_id = ?",
                (rs, i) -> new Entete(rs.getObject("patient_id", UUID.class), rs.getString("numero"),
                        rs.getObject("date_prescription", LocalDate.class), rs.getString("medecin_id")),
                ordonnance, centre.valeur());
        if (entetes.isEmpty()) return Optional.empty();
        Entete e = entetes.getFirst();
        List<LigneImprimable> lignes = jdbc.query("SELECT medicament_code_display, libelle, posologie, voie, duree_jours, quantite, "
                        + "instructions FROM lignes_ordonnance WHERE ordonnance_id = ?",
                (rs, i) -> new LigneImprimable(rs.getString("medicament_code_display"), rs.getString("libelle"), rs.getString("posologie"),
                        rs.getString("voie"), rs.getObject("duree_jours", Integer.class), rs.getObject("quantite", Integer.class),
                        rs.getString("instructions")),
                ordonnance);
        return Optional.of(new OrdonnanceImprimable(e.patient(), e.numero(), e.date(), e.medecin(), lignes));
    }
}
