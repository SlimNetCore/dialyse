package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.CompetenceInfirmier;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.IndisponibiliteGenerateur;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.model.PreferencePatient;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.PreferencePatientPort;
import com.hemodialyse.backend.domain.planning.optimisation.port.ProfilInfirmierPort;
import com.hemodialyse.backend.domain.planning.port.DeplacementTemporairePort;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Données à optimiser : planning de présence du centre sur l'horizon (via {@link PresenceDonneesPort}), patients placés
 * (via les occupations du planning) et patients en attente de place, avec leurs préférences, leurs transporteurs et les
 * compétences qu'ils exigent des infirmiers ; profils des infirmiers ; indisponibilités des générateurs (interventions
 * GMAO planifiées ou en cours) et déplacements temporaires déjà enregistrés. Toutes les requêtes sont bornées au centre
 * (AGENTS.md §2) ; compatible H2 et PostgreSQL.
 */
@Component
public class OptimisationDonneesJdbcAdapter implements OptimisationDonneesPort {

    /**
     * Âge en dessous duquel un patient est pédiatrique.
     */
    static final int AGE_ADULTE = 18;
    private static final String JOURS = "jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, "
            + "jour_samedi";

    private final JdbcTemplate jdbc;
    private final PresenceDonneesPort presence;
    private final PlanningDonneesPort planning;
    private final PreferencePatientPort preferences;
    private final ProfilInfirmierPort profils;
    private final DeplacementTemporairePort temporaires;

    public OptimisationDonneesJdbcAdapter(JdbcTemplate jdbc, PresenceDonneesPort presence, PlanningDonneesPort planning,
                                          PreferencePatientPort preferences, ProfilInfirmierPort profils,
                                          DeplacementTemporairePort temporaires) {
        this.jdbc = jdbc;
        this.presence = presence;
        this.planning = planning;
        this.preferences = preferences;
        this.profils = profils;
        this.temporaires = temporaires;
    }

    private record InfosPatient(String nom, Set<UUID> transporteurs, LocalDate naissance) {
    }

    @Override
    public DonneesOptimisation charger(UUID centerId, LocalDate du, LocalDate au) {
        DonneesPresence donneesPresence = presence.charger(centerId, du, au);
        Map<UUID, InfosPatient> infos = new HashMap<>();
        jdbc.query("SELECT id, nom, prenom, date_naissance, transporteur_aller_id, transporteur_retour_id FROM patients "
                + "WHERE center_id = ?", rs -> {
            Set<UUID> transporteurs = new HashSet<>();
            UUID aller = rs.getObject("transporteur_aller_id", UUID.class);
            UUID retour = rs.getObject("transporteur_retour_id", UUID.class);
            if (aller != null) transporteurs.add(aller);
            if (retour != null) transporteurs.add(retour);
            Date naissance = rs.getDate("date_naissance");
            infos.put(rs.getObject("id", UUID.class), new InfosPatient(nomComplet(rs.getString("prenom"),
                    rs.getString("nom")), transporteurs, naissance == null ? null : naissance.toLocalDate()));
        }, centerId);
        Set<UUID> catheters = new HashSet<>(jdbc.query("SELECT DISTINCT patient_id FROM abords_vasculaires "
                        + "WHERE center_id = ? AND actif = TRUE AND type_abord IN ('KT_TUNNELISE', 'KT_AIGU') "
                        + "AND (date_fin IS NULL OR date_fin >= ?)",
                (rs, i) -> rs.getObject("patient_id", UUID.class), centerId, Date.valueOf(du)));
        Map<UUID, PreferencePatient> prefs = preferences.preferences(centerId);
        Map<UUID, String> codes = new HashMap<>();
        for (GenerateurRef g : donneesPresence.planning().generateurs()) codes.put(g.id(), g.code());

        List<PatientAPlacer> patients = new ArrayList<>();
        Set<UUID> places = new HashSet<>();
        for (Occupation o : donneesPresence.planning().occupations()) {
            places.add(o.patientId());
            Poste actuelle = new Poste(o.salleId(), o.creneauId(), o.generateurId(),
                    o.generateurId() == null ? null : codes.get(o.generateurId()));
            patients.add(patient(o.patientId(), o.jours(), o.aRisque(), actuelle, o.premierJour(), o.dernierJour(),
                    infos, catheters, prefs, du));
        }
        patients.addAll(enAttente(centerId, du, places, infos, catheters, prefs));
        patients.sort(Comparator.comparing(PatientAPlacer::nom).thenComparing(PatientAPlacer::patientId));
        return new DonneesOptimisation(donneesPresence, patients, profils.profils(centerId),
                indisponibilites(centerId, du, au), temporaires.entre(centerId, du, au));
    }

    private static PatientAPlacer patient(UUID id, Set<JourSemaine> jours, boolean aRisque, Poste actuelle,
                                          LocalDate premierJour, LocalDate dernierJour, Map<UUID, InfosPatient> infos,
                                          Set<UUID> catheters, Map<UUID, PreferencePatient> prefs, LocalDate du) {
        InfosPatient info = infos.getOrDefault(id, new InfosPatient("", Set.of(), null));
        PreferencePatient preference = prefs.getOrDefault(id, PreferencePatient.aucune(id));
        Set<CompetenceInfirmier> competences = EnumSet.noneOf(CompetenceInfirmier.class);
        if (info.naissance() != null && info.naissance().plusYears(AGE_ADULTE).isAfter(du)) {
            competences.add(CompetenceInfirmier.PEDIATRIE);
        }
        if (catheters.contains(id)) competences.add(CompetenceInfirmier.CATHETER);
        return new PatientAPlacer(id, info.nom(), jours, aRisque, actuelle, premierJour, dernierJour,
                preference.creneauPrefereId(), preference.joursAChoisir() ? preference.seancesParSemaine() : null,
                info.transporteurs(), competences);
    }

    /**
     * Patients actifs sans place complète (salle ou créneau absent) qui ont des jours de dialyse prescrits, ou dont la
     * préférence demande de choisir les jours. Un patient sorti ou dont le séjour est terminé n'est pas à placer.
     */
    private List<PatientAPlacer> enAttente(UUID centerId, LocalDate du, Set<UUID> dejaPlaces,
                                           Map<UUID, InfosPatient> infos, Set<UUID> catheters,
                                           Map<UUID, PreferencePatient> prefs) {
        Set<UUID> aRisque = planning.patientsARisque(centerId);
        List<PatientAPlacer> attente = new ArrayList<>();
        jdbc.query("SELECT id, salle_id, position_id, generateur_id, etat_patient, date_evenement_etat, "
                + "date_admission, " + JOURS + " FROM patients WHERE center_id = ? "
                + "AND (salle_id IS NULL OR position_id IS NULL)", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            if (dejaPlaces.contains(id)) return;
            Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
            for (JourSemaine jour : JourSemaine.values()) {
                if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
            }
            PreferencePatient preference = prefs.get(id);
            if (jours.isEmpty() && (preference == null || !preference.joursAChoisir())) return;
            String etat = rs.getString("etat_patient");
            Date evenement = rs.getDate("date_evenement_etat");
            LocalDate dernierJour = FinOccupation.dernierJourOccupe(etat,
                    evenement == null ? null : evenement.toLocalDate()).orElse(null);
            if (dernierJour != null && du.isAfter(dernierJour)) return;
            UUID salle = rs.getObject("salle_id", UUID.class);
            UUID creneau = rs.getObject("position_id", UUID.class);
            UUID generateur = rs.getObject("generateur_id", UUID.class);
            Poste actuelle = salle == null && creneau == null && generateur == null ? null
                    : new Poste(salle, creneau, generateur, null);
            Date admission = rs.getDate("date_admission");
            attente.add(patient(id, jours, aRisque.contains(id), actuelle,
                    admission == null ? null : admission.toLocalDate(), dernierJour, infos, catheters, prefs, du));
        }, centerId);
        return attente;
    }

    /**
     * Interventions GMAO planifiées ou en cours sur les générateurs du centre qui chevauchent l'horizon. Sans date de
     * fin, une intervention planifiée immobilise son jour de début, une intervention en cours tout l'horizon.
     */
    private List<IndisponibiliteGenerateur> indisponibilites(UUID centerId, LocalDate du, LocalDate au) {
        return jdbc.query("SELECT i.equipement_id, i.statut, i.type, i.date_debut, i.date_fin FROM gmao_interventions i "
                        + "JOIN gmao_equipements e ON e.id = i.equipement_id AND e.centre_id = i.centre_id "
                        + "WHERE i.centre_id = ? AND i.deleted_at IS NULL AND i.statut IN ('PLANIFIEE', 'EN_COURS') "
                        + "AND e.type = 'GENERATEUR_DIALYSE' AND i.date_debut < ?",
                (rs, i) -> {
                    LocalDate debut = rs.getObject("date_debut", Timestamp.class).toLocalDateTime().toLocalDate();
                    Timestamp finTs = rs.getObject("date_fin", Timestamp.class);
                    LocalDate fin = finTs != null ? finTs.toLocalDateTime().toLocalDate()
                            : "EN_COURS".equals(rs.getString("statut")) ? au : debut;
                    return new IndisponibiliteGenerateur(rs.getObject("equipement_id", UUID.class), debut,
                            fin.isBefore(debut) ? debut : fin, rs.getString("type"));
                }, centerId, Timestamp.valueOf(au.plusDays(1).atStartOfDay()))
                .stream().filter(i -> !i.fin().isBefore(du)).toList();
    }

    private static String nomComplet(String prenom, String nom) {
        return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
    }
}
