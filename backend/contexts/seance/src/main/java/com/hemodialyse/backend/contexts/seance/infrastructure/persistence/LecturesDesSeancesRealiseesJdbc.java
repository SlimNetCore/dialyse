package com.hemodialyse.backend.contexts.seance.infrastructure.persistence;

import com.hemodialyse.backend.contexts.seance.domain.FichesDesPatientsDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectures publiées de la Séance pour les Absences de patients et la Planification (ADR 0098, SE7d), sur la table {@code seances}, lecture
 * seule. Les requêtes sont celles des ACL qu'elles remplacent, MOT POUR MOT : Absences {@code legacyseance/SeancesRealiseesJdbc} (A1 à A3),
 * {@code legacyseance/ExclusionsDeSeancesJdbc} (A4), la requête {@code seances} de {@code legacyplanning/PatientsAttendusJdbc} (A5) ;
 * Planification {@code legacyseance/SeancesRealiseesJdbc} (P1). Reproduits sans correction : casse et blancs du statut (KB-SEA-82),
 * jointure de la fiche au centre de la séance (KB-SEA-80, résidu D-R3, entrée de {@code TableDuPatientCouvertureTest}).
 * Corrigés le 2026-11-10 (lot L5a, ADR 0117) : tri déterministe des jours réalisés du centre, date puis patient (KB-SEA-77) ; tri des
 * séances avec leur place par date, création connue d'abord (la plus ancienne créée) puis identifiant, la première lue par la Planification
 * étant donc la plus ancienne créée (KB-SEA-78) ; {@code absenceLiee} ne bloque que si la séance liée est réellement {@code ABSENT},
 * un lien résiduel sur une autre séance reste en base sans bloquer (KB-SEA-81). N'ouvre aucune transaction.
 */
public class LecturesDesSeancesRealiseesJdbc implements SeancesRealiseesPubliees, LiensDExclusionPublies, SeancesRealiseesAvecLeurPlacePubliees {

    private static final String REALISEE = "s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')";

    private final JdbcTemplate jdbc;
    private final FichesDesPatientsDuCentre fichesDesPatients;

    public LecturesDesSeancesRealiseesJdbc(JdbcTemplate jdbc, FichesDesPatientsDuCentre fichesDesPatients) {
        this.jdbc = jdbc;
        this.fichesDesPatients = fichesDesPatients;
    }

    // ---- Absences de patients (A1 à A3, A5)

    @Override
    public List<JourRealisePublie> joursRealisesDuCentre(UUID centre, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT s.patient_id, s.date_seance FROM seances s WHERE s.center_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND " + REALISEE + " ORDER BY s.date_seance, s.patient_id",
                (rs, i) -> new JourRealisePublie(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate()),
                centre, Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public List<LocalDate> joursRealisesDuPatient(UUID centre, UUID patient, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT DISTINCT s.date_seance FROM seances s WHERE s.center_id = ? AND s.patient_id = ? "
                        + "AND s.date_seance BETWEEN ? AND ? AND " + REALISEE + " ORDER BY s.date_seance",
                (rs, i) -> rs.getDate(1).toLocalDate(), centre, patient, Date.valueOf(du), Date.valueOf(au));
    }

    @Override
    public boolean estRealisee(UUID centre, UUID patient, LocalDate jour) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances s WHERE s.center_id = ? AND s.patient_id = ? "
                + "AND s.date_seance = ? AND " + REALISEE, Integer.class, centre, patient, Date.valueOf(jour));
        return n != null && n > 0;
    }

    @Override
    public List<UUID> patientsRealisesLe(UUID centre, LocalDate jour) {
        return jdbc.queryForList("SELECT s.patient_id FROM seances s WHERE s.center_id = ? AND s.date_seance = ? "
                + "AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE')", UUID.class, centre, Date.valueOf(jour));
    }

    // ---- Absences de patients (A4)

    @Override
    public boolean absenceLiee(UUID centre, UUID absence) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM seances WHERE center_id = ? AND exclusion_absence_id = ? AND statut = 'ABSENT'",
                Integer.class, centre, absence);
        return n != null && n > 0;
    }

    // ---- Planification (P1)

    @Override
    public List<SeanceRealiseeAvecPlacePubliee> duCentre(UUID centre, LocalDate du, LocalDate au) {
        // LEC-PAT (ii) (ADR 0177) : la jointure de la fiche au centre de la séance (KB-SEA-80, reproduit) est faite par la fiche de Patient et
        // couverture, bornée au centre : une séance dont le patient n'est pas une fiche du centre est écartée, comme l'ancienne jointure interne.
        Map<UUID, FichesDesPatientsDuCentre.NomDuPatient> noms = fichesDesPatients.noms(centre);
        List<SeanceRealiseeAvecPlacePubliee> seances = new ArrayList<>();
        jdbc.query("SELECT s.patient_id, s.date_seance, s.salle_id, s.creneau_id, s.generateur_id FROM seances s "
                        + "WHERE s.center_id = ? AND s.date_seance BETWEEN ? AND ? AND s.statut IN ('VALIDEE', 'SIGNEE', 'FACTUREE') "
                        + "ORDER BY s.date_seance, s.created_at NULLS LAST, s.id",
                rs -> {
                    UUID patient = rs.getObject("patient_id", UUID.class);
                    FichesDesPatientsDuCentre.NomDuPatient nom = noms.get(patient);
                    if (nom == null) return;
                    seances.add(new SeanceRealiseeAvecPlacePubliee(patient, rs.getDate("date_seance").toLocalDate(),
                            rs.getObject("salle_id", UUID.class), rs.getObject("creneau_id", UUID.class),
                            rs.getObject("generateur_id", UUID.class), nom.nom(), nom.prenom()));
                },
                centre, Date.valueOf(du), Date.valueOf(au));
        return seances;
    }
}
