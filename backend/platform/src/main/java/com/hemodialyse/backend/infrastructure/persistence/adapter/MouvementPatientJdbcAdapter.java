package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.patient.model.MouvementLigne;
import com.hemodialyse.backend.domain.patient.model.MouvementPatient;
import com.hemodialyse.backend.domain.patient.model.TypeMouvementPatient;
import com.hemodialyse.backend.domain.patient.port.MouvementPatientRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Historique des mouvements de patients sur la table {@code mouvement_patient} (écriture seule, aucune mise à jour).
 */
@Component
public class MouvementPatientJdbcAdapter implements MouvementPatientRepositoryPort {

    private static final String FROM = " FROM mouvement_patient m "
            + "JOIN patients p ON p.id = m.patient_id AND p.center_id = m.center_id "
            + "LEFT JOIN salle s ON s.id = m.salle_id "
            + "LEFT JOIN position_creneau c ON c.id = m.position_id "
            + "LEFT JOIN gmao_equipements g ON g.id = m.generateur_id ";

    private final JdbcTemplate jdbc;

    public MouvementPatientJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String nomComplet(String prenom, String nom) {
        if (nom == null) return null;
        return prenom == null || prenom.isBlank() ? nom : prenom + " " + nom;
    }

    @Override
    public void save(MouvementPatient m) {
        jdbc.update("INSERT INTO mouvement_patient (id, center_id, patient_id, type, date_effet, etat_precedent, "
                        + "etat_nouveau, salle_id, position_id, generateur_id, jours_dialyse, automatique, cree_le) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                m.id(), m.centerId(), m.patientId(), m.type().name(), Date.valueOf(m.dateEffet()), m.etatPrecedent(),
                m.etatNouveau(), m.salleId(), m.positionId(), m.generateurId(), m.joursDialyse(), m.automatique(),
                Timestamp.from(m.creeLe()));
    }

    @Override
    public PagedResult<MouvementLigne> findPaged(UUID centerId, UUID patientId, TypeMouvementPatient type, LocalDate du,
                                                 LocalDate au, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE m.center_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(centerId);
        if (patientId != null) {
            where.append(" AND m.patient_id = ?");
            args.add(patientId);
        }
        if (type != null) {
            where.append(" AND m.type = ?");
            args.add(type.name());
        }
        if (du != null) {
            where.append(" AND m.date_effet >= ?");
            args.add(Date.valueOf(du));
        }
        if (au != null) {
            where.append(" AND m.date_effet <= ?");
            args.add(Date.valueOf(au));
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<MouvementLigne> items = jdbc.query("SELECT m.id, m.center_id, m.patient_id, m.type, m.date_effet, "
                        + "m.etat_precedent, m.etat_nouveau, m.salle_id, m.position_id, m.generateur_id, m.jours_dialyse, "
                        + "m.automatique, m.cree_le, p.nom, p.prenom, p.code_patient, s.nom AS salle_nom, "
                        + "c.libelle AS creneau, g.code AS generateur_code" + FROM + where
                        + " ORDER BY m.date_effet DESC, m.cree_le DESC, m.id LIMIT ? OFFSET ?",
                (rs, i) -> new MouvementLigne(
                        new MouvementPatient(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class),
                                rs.getObject("patient_id", UUID.class), TypeMouvementPatient.valueOf(rs.getString("type")),
                                rs.getDate("date_effet").toLocalDate(), rs.getString("etat_precedent"),
                                rs.getString("etat_nouveau"), rs.getObject("salle_id", UUID.class),
                                rs.getObject("position_id", UUID.class), rs.getObject("generateur_id", UUID.class),
                                rs.getString("jours_dialyse"), rs.getBoolean("automatique"),
                                rs.getTimestamp("cree_le").toInstant()),
                        nomComplet(rs.getString("prenom"), rs.getString("nom")), rs.getString("code_patient"),
                        rs.getString("salle_nom"), rs.getString("creneau"), rs.getString("generateur_code")),
                pageArgs.toArray());
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }
}
