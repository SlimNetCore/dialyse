package com.hemodialyse.backend.infrastructure.persistence.adapter.optimisation;

import com.hemodialyse.backend.domain.infirmier.model.Presence.DonneesPresence;
import com.hemodialyse.backend.domain.infirmier.port.PresenceDonneesPort;
import com.hemodialyse.backend.domain.patient.service.FinOccupation;
import com.hemodialyse.backend.domain.planning.model.JourSemaine;
import com.hemodialyse.backend.domain.planning.model.Planning.GenerateurRef;
import com.hemodialyse.backend.domain.planning.model.Planning.Occupation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation;
import com.hemodialyse.backend.domain.planning.optimisation.model.DonneesOptimisation.PatientAPlacer;
import com.hemodialyse.backend.domain.planning.optimisation.model.Poste;
import com.hemodialyse.backend.domain.planning.optimisation.port.OptimisationDonneesPort;
import com.hemodialyse.backend.domain.planning.port.PlanningDonneesPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * (via les occupations du planning) et patients en attente de place (jours prescrits mais salle ou créneau manquant).
 * Toutes les requêtes sont bornées au centre (AGENTS.md §2) ; compatible H2 et PostgreSQL.
 */
@Component
public class OptimisationDonneesJdbcAdapter implements OptimisationDonneesPort {

    private static final String JOURS = "jour_dimanche, jour_lundi, jour_mardi, jour_mercredi, jour_jeudi, jour_vendredi, "
            + "jour_samedi";

    private final JdbcTemplate jdbc;
    private final PresenceDonneesPort presence;
    private final PlanningDonneesPort planning;

    public OptimisationDonneesJdbcAdapter(JdbcTemplate jdbc, PresenceDonneesPort presence, PlanningDonneesPort planning) {
        this.jdbc = jdbc;
        this.presence = presence;
        this.planning = planning;
    }

    @Override
    public DonneesOptimisation charger(UUID centerId, LocalDate du, LocalDate au) {
        DonneesPresence donneesPresence = presence.charger(centerId, du, au);
        Map<UUID, String> noms = new HashMap<>();
        jdbc.query("SELECT id, nom, prenom FROM patients WHERE center_id = ? AND salle_id IS NOT NULL "
                        + "AND position_id IS NOT NULL",
                rs -> {
                    noms.put(rs.getObject("id", UUID.class), nomComplet(rs.getString("prenom"), rs.getString("nom")));
                }, centerId);
        Map<UUID, String> codes = new HashMap<>();
        for (GenerateurRef g : donneesPresence.planning().generateurs()) codes.put(g.id(), g.code());

        List<PatientAPlacer> patients = new ArrayList<>();
        Set<UUID> places = new HashSet<>();
        for (Occupation o : donneesPresence.planning().occupations()) {
            places.add(o.patientId());
            Poste actuelle = new Poste(o.salleId(), o.creneauId(), o.generateurId(),
                    o.generateurId() == null ? null : codes.get(o.generateurId()));
            patients.add(new PatientAPlacer(o.patientId(), noms.getOrDefault(o.patientId(), ""), o.jours(), o.aRisque(),
                    actuelle, o.premierJour(), o.dernierJour()));
        }
        patients.addAll(enAttente(centerId, du, places));
        patients.sort(java.util.Comparator.comparing(PatientAPlacer::nom).thenComparing(PatientAPlacer::patientId));
        return new DonneesOptimisation(donneesPresence, patients);
    }

    /**
     * Patients actifs qui ont des jours de dialyse mais pas de place complète (salle ou créneau absent) : ils sont à
     * placer. Un patient sorti (transfert, décès, greffe, guérison) ou dont le séjour est terminé n'est pas à placer.
     */
    private List<PatientAPlacer> enAttente(UUID centerId, LocalDate du, Set<UUID> dejaPlaces) {
        Set<UUID> aRisque = planning.patientsARisque(centerId);
        List<PatientAPlacer> attente = new ArrayList<>();
        jdbc.query("SELECT id, nom, prenom, salle_id, position_id, generateur_id, etat_patient, date_evenement_etat, "
                + "date_admission, " + JOURS + " FROM patients WHERE center_id = ? "
                + "AND (salle_id IS NULL OR position_id IS NULL)", rs -> {
            UUID id = rs.getObject("id", UUID.class);
            if (dejaPlaces.contains(id)) return;
            Set<JourSemaine> jours = EnumSet.noneOf(JourSemaine.class);
            for (JourSemaine jour : JourSemaine.values()) {
                if (rs.getBoolean("jour_" + jour.name().toLowerCase(Locale.ROOT))) jours.add(jour);
            }
            if (jours.isEmpty()) return;
            Date evenement = rs.getDate("date_evenement_etat");
            LocalDate dernierJour = FinOccupation.dernierJourOccupe(rs.getString("etat_patient"),
                    evenement == null ? null : evenement.toLocalDate()).orElse(null);
            if (dernierJour != null && du.isAfter(dernierJour)) return;
            UUID salle = rs.getObject("salle_id", UUID.class);
            UUID creneau = rs.getObject("position_id", UUID.class);
            UUID generateur = rs.getObject("generateur_id", UUID.class);
            Poste actuelle = salle == null && creneau == null && generateur == null ? null
                    : new Poste(salle, creneau, generateur, null);
            Date admission = rs.getDate("date_admission");
            attente.add(new PatientAPlacer(id, nomComplet(rs.getString("prenom"), rs.getString("nom")), jours,
                    aRisque.contains(id), actuelle, admission == null ? null : admission.toLocalDate(), dernierJour));
        }, centerId);
        return attente;
    }

    private static String nomComplet(String prenom, String nom) {
        return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
    }
}
