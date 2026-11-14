package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients.NomDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter du port publié {@link LotsDePatients} sur la table {@code patients} : les lectures que la Séance faisait par jointure
 * {@code seances x patients} (journal du jour, présents du mois, identités du détail nominatif), reprises à l'identique. La jointure se
 * fait sur l'identifiant seul (KB-SEA-61, KB-SEA-64) ; le tri est celui de la base ({@code ORDER BY nom ASC, prenom ASC}).
 */
public class LotsDePatientsJdbc implements LotsDePatients {

    private final JdbcTemplate jdbc;

    public LotsDePatientsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, NomDuPatient> identitesDuCentre(UUID centre) {
        Map<UUID, NomDuPatient> identites = new LinkedHashMap<>();
        jdbc.query("SELECT id, nom, prenom FROM patients WHERE center_id = ?",
                (rs, i) -> identites.put(rs.getObject("id", UUID.class), new NomDuPatient(rs.getString("prenom"), rs.getString("nom"))),
                centre);
        return identites;
    }

    @Override
    public List<EtatCivilDuPatient> etatCivil(Collection<UUID> patients) {
        Set<UUID> demandes = new LinkedHashSet<>(patients);
        if (demandes.isEmpty()) return List.of();
        Map<UUID, EtatCivilDuPatient> lus = new LinkedHashMap<>();
        jdbc.query("SELECT id, sexe, date_naissance FROM patients WHERE id IN (" + marques(demandes.size()) + ")",
                (rs, i) -> lus.put(rs.getObject("id", UUID.class), new EtatCivilDuPatient(rs.getObject("id", UUID.class),
                        rs.getString("sexe"), date(rs.getObject("date_naissance")))),
                demandes.toArray());
        List<EtatCivilDuPatient> rendus = new ArrayList<>();
        for (UUID id : demandes) {
            EtatCivilDuPatient lu = lus.get(id);
            if (lu != null) rendus.add(lu);
        }
        return rendus;
    }

    @Override
    public List<PatientDuLot> parNomPuisPrenom(Collection<UUID> patients) {
        Set<UUID> demandes = new LinkedHashSet<>(patients);
        if (demandes.isEmpty()) return List.of();
        List<PatientDuLot> rendus = new ArrayList<>();
        int[] rang = {0};
        String[] precedent = {null, null};
        jdbc.query("SELECT id, code_patient, nom, prenom FROM patients WHERE id IN (" + marques(demandes.size())
                        + ") ORDER BY nom ASC, prenom ASC",
                rs -> {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    if (rang[0] == 0 || !Objects.equals(nom, precedent[0]) || !Objects.equals(prenom, precedent[1])) rang[0]++;
                    precedent[0] = nom;
                    precedent[1] = prenom;
                    rendus.add(new PatientDuLot(rs.getObject("id", UUID.class), rs.getString("code_patient"), nom, prenom, rang[0]));
                }, demandes.toArray());
        return rendus;
    }

    @Override
    public List<PatientAFacturer> aFacturerDuCentre(UUID centre, Collection<UUID> patients) {
        Set<UUID> demandes = new LinkedHashSet<>(patients);
        if (demandes.isEmpty()) return List.of();
        List<PatientAFacturer> rendus = new ArrayList<>();
        int[] rang = {0};
        String[] precedent = {null, null};
        List<Object> criteres = new ArrayList<>();
        criteres.add(centre);
        criteres.addAll(demandes);
        jdbc.query("SELECT id, code_patient, nom, prenom, etat_patient, numero_assurance, centre_payeur_id FROM patients "
                        + "WHERE center_id = ? AND id IN (" + marques(demandes.size()) + ") ORDER BY nom ASC, prenom ASC",
                rs -> {
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    if (rang[0] == 0 || !Objects.equals(nom, precedent[0]) || !Objects.equals(prenom, precedent[1])) rang[0]++;
                    precedent[0] = nom;
                    precedent[1] = prenom;
                    rendus.add(new PatientAFacturer(rs.getObject("id", UUID.class), rs.getString("code_patient"), nom, prenom,
                            rs.getString("etat_patient"), rs.getString("numero_assurance"),
                            rs.getObject("centre_payeur_id", UUID.class), rang[0]));
                }, criteres.toArray());
        return rendus;
    }

    @Override
    public Map<UUID, UUID> centresPayeursDuCentre(UUID centre, Collection<UUID> patients) {
        Set<UUID> demandes = new LinkedHashSet<>(patients);
        if (demandes.isEmpty()) return Map.of();
        Map<UUID, UUID> payeurs = new LinkedHashMap<>();
        List<Object> criteres = new ArrayList<>();
        criteres.add(centre);
        criteres.addAll(demandes);
        jdbc.query("SELECT id, centre_payeur_id FROM patients WHERE center_id = ? AND centre_payeur_id IS NOT NULL AND id IN ("
                        + marques(demandes.size()) + ")",
                (rs, i) -> payeurs.put(rs.getObject("id", UUID.class), rs.getObject("centre_payeur_id", UUID.class)),
                criteres.toArray());
        return payeurs;
    }

    @Override
    public List<PatientDeLaRecherche> rechercherDuCentre(UUID centre, String texte, OrdreDeRecherche ordre, boolean decroissant) {
        StringBuilder requete = new StringBuilder("SELECT p.id, p.code_patient, p.nom, p.prenom FROM patients p WHERE p.center_id = ?");
        List<Object> parametres = new ArrayList<>();
        parametres.add(centre);
        if (texte != null && !texte.isBlank()) {
            for (String mot : texte.toLowerCase(java.util.Locale.ROOT).split("\\s+")) {
                String motif = "%" + mot.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                requete.append(" AND (LOWER(p.nom) LIKE ? ESCAPE '\\' OR LOWER(p.prenom) LIKE ? ESCAPE '\\'"
                        + " OR LOWER(COALESCE(p.code_patient, '')) LIKE ? ESCAPE '\\')");
                parametres.add(motif);
                parametres.add(motif);
                parametres.add(motif);
            }
        }
        String sens = decroissant ? " DESC" : " ASC";
        switch (ordre) {
            case NOM_PUIS_PRENOM -> requete.append(" ORDER BY p.nom").append(sens).append(", p.prenom").append(sens);
            case CODE -> requete.append(" ORDER BY p.code_patient").append(sens);
            case AUCUN -> {
            }
        }
        List<PatientDeLaRecherche> rendus = new ArrayList<>();
        int[] rang = {0};
        String[] precedent = {null, null};
        jdbc.query(requete.toString(), rs -> {
            String code = rs.getString("code_patient");
            String nom = rs.getString("nom");
            String prenom = rs.getString("prenom");
            boolean change = switch (ordre) {
                case NOM_PUIS_PRENOM -> !Objects.equals(nom, precedent[0]) || !Objects.equals(prenom, precedent[1]);
                case CODE -> !Objects.equals(code, precedent[0]);
                case AUCUN -> false;
            };
            if (rang[0] == 0 || change) rang[0]++;
            precedent[0] = ordre == OrdreDeRecherche.CODE ? code : nom;
            precedent[1] = prenom;
            rendus.add(new PatientDeLaRecherche(rs.getObject("id", UUID.class), code, nom, prenom, rang[0]));
        }, parametres.toArray());
        return rendus;
    }

    private static String marques(int nombre) {
        return String.join(",", java.util.Collections.nCopies(nombre, "?"));
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate d) return d;
        if (valeur instanceof Date d) return d.toLocalDate();
        return null;
    }
}
