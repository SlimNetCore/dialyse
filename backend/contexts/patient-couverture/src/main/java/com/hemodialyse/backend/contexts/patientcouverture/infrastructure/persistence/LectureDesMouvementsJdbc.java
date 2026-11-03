package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lecture du journal {@code mouvement_patient} : même requête, même ordre et mêmes jointures que la liste actuelle (un
 * mouvement dont le patient n'est pas du centre est masqué ; salle, créneau et générateur sont facultatifs).
 */
public class LectureDesMouvementsJdbc implements LectureDesMouvements {

    private static final String DEPUIS = " FROM mouvement_patient m "
            + "JOIN patients p ON p.id = m.patient_id AND p.center_id = m.center_id "
            + "LEFT JOIN salle s ON s.id = m.salle_id "
            + "LEFT JOIN position_creneau c ON c.id = m.position_id "
            + "LEFT JOIN gmao_equipements g ON g.id = m.generateur_id ";

    private final JdbcTemplate jdbc;

    public LectureDesMouvementsJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page lister(CentreId centre, Filtre filtre, int page, int size) {
        StringBuilder ou = new StringBuilder(" WHERE m.center_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(centre.valeur());
        if (filtre.patient() != null) {
            ou.append(" AND m.patient_id = ?");
            args.add(filtre.patient());
        }
        if (filtre.type() != null) {
            ou.append(" AND m.type = ?");
            args.add(filtre.type().name());
        }
        if (filtre.du() != null) {
            ou.append(" AND m.date_effet >= ?");
            args.add(Date.valueOf(filtre.du()));
        }
        if (filtre.au() != null) {
            ou.append(" AND m.date_effet <= ?");
            args.add(Date.valueOf(filtre.au()));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + DEPUIS + ou, Long.class, args.toArray());
        List<Object> argsPage = new ArrayList<>(args);
        argsPage.add(size);
        argsPage.add((long) page * size);
        List<Ligne> lignes = jdbc.query("SELECT m.id, m.patient_id, m.type, m.date_effet, m.etat_precedent, m.etat_nouveau, "
                + "m.jours_dialyse, m.automatique, m.cree_le, p.nom, p.prenom, p.code_patient, s.nom AS salle_nom, "
                + "c.libelle AS creneau, g.code AS generateur_code" + DEPUIS + ou
                + " ORDER BY m.date_effet DESC, m.cree_le DESC, m.id LIMIT ? OFFSET ?",
                (rs, i) -> new Ligne(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        nomComplet(rs.getString("prenom"), rs.getString("nom")), rs.getString("code_patient"),
                        type(rs.getString("type")), rs.getDate("date_effet").toLocalDate(),
                        rs.getString("etat_precedent"), rs.getString("etat_nouveau"), rs.getString("salle_nom"),
                        rs.getString("creneau"), rs.getString("generateur_code"), rs.getString("jours_dialyse"),
                        rs.getBoolean("automatique"), rs.getTimestamp("cree_le").toInstant()),
                argsPage.toArray());
        return new Page(lignes, total == null ? 0 : total, page, size);
    }

    /** Un type hors liste en base fait échouer la liste non filtrée en 400 (reproduit, KB-PAT-13), avec un message neutre. */
    private static TypeDeMouvement type(String brut) {
        try {
            return TypeDeMouvement.valueOf(brut);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide(
                    "Type de mouvement inconnu en base : " + brut);
        }
    }

    private static String nomComplet(String prenom, String nom) {
        if (nom == null) return null;
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }
}
