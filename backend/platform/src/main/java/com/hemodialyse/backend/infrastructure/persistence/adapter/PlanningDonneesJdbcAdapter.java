package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.CreneauRef;
import com.hemodialyse.backend.domain.planning.model.Planning.DonneesPlanning;
import com.hemodialyse.backend.domain.planning.model.Planning.Fermeture;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.model.Planning.SalleRef;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.model.PlanningSemaine.DonneesSemaine;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningParametresPort;
import com.hemodialyse.backend.domain.planning.port.PlanningSemainePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lecture du planning d'un centre : salles, créneaux (positions), générateurs en service, patients actifs déjà placés
 * (avec leur risque infectieux), jours d'ouverture, salles d'isolement et fermetures datées. Toutes les requêtes sont
 * bornées au centre (AGENTS.md §2) ; compatible H2 et PostgreSQL.
 * <p>
 * Un patient transféré, décédé, greffé ou guéri ne tient plus de place. Un patient « en sommeil » garde la sienne
 * (choix prudent : éviter un double placement à son retour). Un patient est à risque si sa dernière sérologie positive
 * concerne l'AgHBs, l'anti-VHC, l'ARN du VHC ou le VIH.
 */
@Component
public class PlanningDonneesJdbcAdapter implements PlanningDonneesPort, PlanningSemainePort {

    /**
     * Horizon des fermetures datées signalées sur une proposition de placement.
     */
    static final int HORIZON_FERMETURES_JOURS = 90;

    private static final String PATIENTS_ACTIFS =
            "(etat_patient IS NULL OR etat_patient NOT IN ('TRANSFERE', 'DECEDE', 'GREFFE', 'GUERRI'))";

    /**
     * Patients dont la dernière sérologie d'au moins un marqueur à risque est positive.
     */
    private static final String PATIENTS_A_RISQUE =
            "SELECT DISTINCT s.patient_id FROM serologies_patient s "
                    + "WHERE s.center_id = ? AND s.marqueur IN ('AG_HBS', 'AC_VHC', 'ARN_VHC', 'VIH_AC') "
                    + "AND s.resultat = 'POSITIF' "
                    + "AND NOT EXISTS (SELECT 1 FROM serologies_patient n WHERE n.center_id = s.center_id "
                    + "AND n.patient_id = s.patient_id AND n.marqueur = s.marqueur "
                    + "AND (n.date_prelevement > s.date_prelevement "
                    + "OR (n.date_prelevement = s.date_prelevement AND n.created_at > s.created_at)))";

    private final JdbcTemplate jdbc;
    private final PlanningParametresPort parametres;

    public PlanningDonneesJdbcAdapter(JdbcTemplate jdbc, PlanningParametresPort parametres) {
        this.jdbc = jdbc;
        this.parametres = parametres;
    }

    @Override
    public DonneesPlanning charger(UUID centerId, UUID patientAIgnorer) {
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        return construire(centerId, patientAIgnorer, aujourdhui, aujourdhui.plusDays(HORIZON_FERMETURES_JOURS));
    }

    @Override
    public DonneesSemaine charger(UUID centerId, LocalDate debutSemaine) {
        LocalDate fin = debutSemaine.plusDays(6);
        DonneesPlanning planning = construire(centerId, null, debutSemaine, fin);
        Map<UUID, String> noms = new HashMap<>();
        jdbc.query("SELECT id, nom, prenom FROM patients WHERE center_id = ? AND salle_id IS NOT NULL AND "
                        + PATIENTS_ACTIFS,
                rs -> {
                    noms.put(rs.getObject("id", UUID.class),
                            (rs.getString("prenom") + " " + rs.getString("nom")).trim());
                }, centerId);
        return new DonneesSemaine(planning, noms, planning.fermetures());
    }

    @Override
    public DonneesPlanning chargerPeriode(UUID centerId, LocalDate du, LocalDate au) {
        return construire(centerId, null, du, au);
    }

    @Override
    public Set<UUID> creneauxDuCentre(UUID centerId) {
        return new HashSet<>(jdbc.query("SELECT id FROM position_creneau WHERE center_id = ?",
                (rs, i) -> rs.getObject("id", UUID.class), centerId));
    }

    @Override
    public boolean patientARisque(UUID centerId, UUID patientId) {
        return new HashSet<>(jdbc.query(PATIENTS_A_RISQUE, (rs, i) -> rs.getObject("patient_id", UUID.class), centerId))
                .contains(patientId);
    }

    @Override
    public Set<UUID> sallesDuCentre(UUID centerId) {
        return new HashSet<>(jdbc.query("SELECT id FROM salle WHERE center_id = ?",
                (rs, i) -> rs.getObject("id", UUID.class), centerId));
    }

    private DonneesPlanning construire(UUID centerId, UUID patientAIgnorer, LocalDate fermeturesDu, LocalDate fermeturesAu) {
        List<SalleRef> salles = jdbc.query(
                "SELECT id, nom FROM salle WHERE center_id = ? ORDER BY code",
                (rs, i) -> new SalleRef(rs.getObject("id", UUID.class), rs.getString("nom")), centerId);

        AtomicInteger rang = new AtomicInteger();
        List<CreneauRef> creneaux = jdbc.query(
                "SELECT id, libelle FROM position_creneau WHERE center_id = ? ORDER BY code",
                (rs, i) -> new CreneauRef(rs.getObject("id", UUID.class), rs.getString("libelle"), rang.incrementAndGet()),
                centerId);

        List<GenerateurRef> generateurs = jdbc.query(
                "SELECT id, code, salle_id FROM gmao_equipements WHERE centre_id = ? AND type = 'GENERATEUR_DIALYSE' "
                        + "AND statut = 'EN_SERVICE' AND deleted_at IS NULL AND salle_id IS NOT NULL ORDER BY code",
                (rs, i) -> new GenerateurRef(rs.getObject("id", UUID.class), rs.getString("code"),
                        rs.getObject("salle_id", UUID.class)),
                centerId);

        Set<UUID> aRisque = new HashSet<>(jdbc.query(PATIENTS_A_RISQUE,
                (rs, i) -> rs.getObject("patient_id", UUID.class), centerId));

        String sql = "SELECT id, salle_id, position_id, generateur_id, jour_dimanche, jour_lundi, jour_mardi, "
                + "jour_mercredi, jour_jeudi, jour_vendredi, jour_samedi FROM patients "
                + "WHERE center_id = ? AND salle_id IS NOT NULL AND position_id IS NOT NULL AND " + PATIENTS_ACTIFS
                + (patientAIgnorer != null ? " AND id <> ?" : "");
        Object[] args = patientAIgnorer != null ? new Object[]{centerId, patientAIgnorer} : new Object[]{centerId};
        List<Occupation> occupations = new ArrayList<>(jdbc.query(sql, (rs, i) -> {
            Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
            for (JourSemaine jour : JourSemaine.values()) {
                if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
            }
            UUID patientId = rs.getObject("id", UUID.class);
            return new Occupation(patientId, rs.getObject("salle_id", UUID.class),
                    rs.getObject("position_id", UUID.class), rs.getObject("generateur_id", UUID.class), jours,
                    aRisque.contains(patientId));
        }, args));
        occupations.removeIf(o -> o.jours().isEmpty());

        PlanningParametres params = parametres.lire(centerId);
        return new DonneesPlanning(salles, creneaux, generateurs, occupations, params.joursOuverts(),
                params.sallesIsolement(), fermetures(centerId, fermeturesDu, fermeturesAu));
    }

    /**
     * Fériés et fermetures exceptionnelles du centre entre deux dates (tables du calendrier clinique, absentes sur
     * les très anciennes bases : aucune fermeture dans ce cas).
     */
    private List<Fermeture> fermetures(UUID centerId, LocalDate du, LocalDate au) {
        List<Fermeture> fermetures = new ArrayList<>();
        fermetures.addAll(lireFermetures("SELECT day_date, label AS motif FROM center_holiday "
                + "WHERE center_id = ? AND day_date BETWEEN ? AND ?", centerId, du, au));
        fermetures.addAll(lireFermetures("SELECT day_date, reason AS motif FROM center_closure_day "
                + "WHERE center_id = ? AND day_date BETWEEN ? AND ?", centerId, du, au));
        return fermetures;
    }

    private List<Fermeture> lireFermetures(String sql, UUID centerId, LocalDate du, LocalDate au) {
        try {
            return jdbc.query(sql, (rs, i) -> new Fermeture(
                            rs.getObject("day_date", Date.class).toLocalDate(), rs.getString("motif")),
                    centerId, Date.valueOf(du), Date.valueOf(au));
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
