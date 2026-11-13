package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DonneesMedicalesDeLaReprise;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lecture et suppression des données médicales d'un lot de reprise (RP2, ADR 0170). Requêtes reprises MOT POUR MOT de
 * {@code MigrationRollbackJdbcAdapter} : lecture {@code SELECT CAST(id AS VARCHAR(36)) FROM <table> WHERE patient_id = ?} (aucun centre,
 * KB-S6a-9 reproduit) et suppression {@code DELETE FROM <table> WHERE id = ? AND center_id = ?} (centre du lot). La nature est contrôlée sur
 * la liste fermée du langage publié avant d'être placée dans la requête : aucune valeur de l'appelant n'est concaténée telle quelle. Aucune
 * transaction propre : participe à celle de l'annulation (même {@code JdbcTemplate}).
 */
public class DonneesMedicalesDeLaRepriseJdbc implements DonneesMedicalesDeLaReprise {

    private static final List<String> NATURES_LUES = lues();

    private final JdbcTemplate jdbc;

    public DonneesMedicalesDeLaRepriseJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static List<String> lues() {
        List<String> natures = new ArrayList<>(NATURES_CONTROLEES_AVANT_LES_BONS_DE_SORTIE);
        natures.addAll(NATURES_CONTROLEES_APRES_LES_BONS_DE_SORTIE);
        return List.copyOf(natures);
    }

    @Override
    public List<String> lignesDuPatient(UUID patient, String nature) {
        if (!NATURES_LUES.contains(nature)) {
            throw new IllegalArgumentException("Nature de donnée médicale inconnue : " + nature);
        }
        return jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) FROM " + nature + " WHERE patient_id = ?", String.class, patient);
    }

    @Override
    public void supprimerLaDonneeDuLot(UUID centre, String nature, UUID donnee) {
        if (!NATURES_DU_LOT.contains(nature)) {
            throw new IllegalArgumentException("Nature de donnée médicale non reprise : " + nature);
        }
        jdbc.update("DELETE FROM " + nature + " WHERE id = ? AND center_id = ?", donnee, centre);
    }
}
