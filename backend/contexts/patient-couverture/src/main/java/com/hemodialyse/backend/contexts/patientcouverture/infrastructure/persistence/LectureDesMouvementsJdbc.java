package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.LibellesDeLOrganisation;
import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesMouvements;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TypeDeMouvement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture du journal {@code mouvement_patient} : même requête, même ordre et mêmes jointures que la liste actuelle (un
 * mouvement dont le patient n'est pas du centre est masqué ; salle, créneau et générateur sont facultatifs). Le nom de la salle et le
 * libellé du créneau viennent du langage publié d'Organisation (O4 lot C, ADR 0109), lus par lot sans borne de centre (KB-ORG-10).
 */
public class LectureDesMouvementsJdbc implements LectureDesMouvements {

    private static final String DEPUIS = " FROM mouvement_patient m "
            + "JOIN patients p ON p.id = m.patient_id AND p.center_id = m.center_id "
            + "LEFT JOIN gmao_equipements g ON g.id = m.generateur_id ";

    private final JdbcTemplate jdbc;
    private final LibellesDeLOrganisation libelles;

    public LectureDesMouvementsJdbc(JdbcTemplate jdbc, LibellesDeLOrganisation libelles) {
        this.jdbc = jdbc;
        this.libelles = libelles;
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
        // Le nom de la salle et le libellé du créneau ne sont plus joints : ils sont lus par lot dans le langage publié d'Organisation
        // (O4 lot C, ADR 0109), SANS borne de centre comme l'étaient les jointures. Les jointures sur clé primaire ne changeaient ni
        // l'ordre ni le nombre de lignes.
        List<Object[]> brutes = jdbc.query("SELECT m.id, m.patient_id, m.type, m.date_effet, m.etat_precedent, m.etat_nouveau, "
                + "m.jours_dialyse, m.automatique, m.cree_le, p.nom, p.prenom, p.code_patient, m.salle_id, "
                + "m.position_id, g.code AS generateur_code" + DEPUIS + ou
                + " ORDER BY m.date_effet DESC, m.cree_le DESC, m.id LIMIT ? OFFSET ?",
                (rs, i) -> new Object[]{rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        nomComplet(rs.getString("prenom"), rs.getString("nom")), rs.getString("code_patient"),
                        type(rs.getString("type")), rs.getDate("date_effet").toLocalDate(),
                        rs.getString("etat_precedent"), rs.getString("etat_nouveau"), rs.getObject("salle_id", UUID.class),
                        rs.getObject("position_id", UUID.class), rs.getString("generateur_code"), rs.getString("jours_dialyse"),
                        rs.getBoolean("automatique"), rs.getTimestamp("cree_le").toInstant()},
                argsPage.toArray());
        List<UUID> salles = brutes.stream().map(b -> (UUID) b[8]).toList();
        List<UUID> creneaux = brutes.stream().map(b -> (UUID) b[9]).toList();
        Map<UUID, String> nomsDesSalles = libelles.nomsDesSalles(salles);
        Map<UUID, String> libellesDesCreneaux = libelles.libellesDesCreneaux(creneaux);
        List<Ligne> lignes = new ArrayList<>();
        for (Object[] b : brutes) {
            UUID salle = (UUID) b[8];
            UUID creneau = (UUID) b[9];
            lignes.add(new Ligne((UUID) b[0], (UUID) b[1], (String) b[2], (String) b[3], (TypeDeMouvement) b[4], (LocalDate) b[5],
                    (String) b[6], (String) b[7], salle == null ? null : nomsDesSalles.get(salle),
                    creneau == null ? null : libellesDesCreneaux.get(creneau), (String) b[10], (String) b[11], (Boolean) b[12],
                    (Instant) b[13]));
        }
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
