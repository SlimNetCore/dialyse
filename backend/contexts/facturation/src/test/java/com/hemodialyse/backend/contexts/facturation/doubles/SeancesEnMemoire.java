package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeanceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.seance.SeancesDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PolitiqueDeForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des séances, des patients, des forfaits et des prises en charge vus par la Facturation : même contrat que
 * l'ACL JDBC (voir {@code SeancesContrat}). Il implémente ensemble la lecture des séances facturables et les écritures, pour que
 * l'exclusion retire bien la séance de la simulation.
 */
public class SeancesEnMemoire implements SeancesDeFacturation, SeancesFacturables, ForfaitsDuCentre, LiaisonDesSeances {

    private final Map<UUID, Ligne> seances = new HashMap<>();
    private final Map<UUID, PatientFacture> patients = new HashMap<>();
    private final Map<UUID, UUID> centresDesPatients = new HashMap<>();
    private final Map<UUID, ForfaitApplique> forfaits = new HashMap<>();
    private final Map<UUID, UUID> centresDesForfaits = new HashMap<>();
    private final List<PriseEnChargeDuPatient> prisesEnCharge = new ArrayList<>();

    // ---- données de test

    public void patient(UUID id, CentreId centre, String code, String nom, String prenom, String etat) {
        patients.put(id, new PatientFacture(id, code, nom, prenom, etat));
        centresDesPatients.put(id, centre.valeur());
    }

    public void patient(UUID id, CentreId centre, String code, String nom, String prenom, String etat, String immatriculation,
                        UUID centrePayeur, UUID agence) {
        patients.put(id, new PatientFacture(id, code, nom, prenom, etat, immatriculation, centrePayeur, agence));
        centresDesPatients.put(id, centre.valeur());
    }

    public void forfait(UUID id, CentreId centre, String libelle, java.math.BigDecimal prix) {
        forfaits.put(id, new ForfaitApplique(id, libelle, prix));
        centresDesForfaits.put(id, centre.valeur());
    }

    public void priseEnCharge(UUID patient, PriseEnChargeTarifee pec) {
        prisesEnCharge.add(new PriseEnChargeDuPatient(patient, pec));
    }

    public void seance(UUID id, CentreId centre, UUID patient, LocalDate date, String statut, boolean facturee) {
        seances.put(id, new Ligne(id, centre, patient, date, statut, facturee));
    }

    public void surcharge(UUID seance, ForfaitApplique forfait) {
        seances.get(seance).surcharge = forfait;
    }

    // ---- lecture des séances facturables

    @Override
    public List<SeanceFacturable> dansLaPeriode(CentreId centre, PeriodeDeFacturation periode) {
        return seances.values().stream()
                .filter(s -> s.centre.equals(centre) && !s.facturee && ("VALIDEE".equals(s.statut) || "SIGNEE".equals(s.statut)))
                .filter(s -> !s.date.isBefore(periode.debut()) && !s.date.isAfter(periode.fin()))
                .sorted(Comparator.<Ligne, String>comparing(s -> patients.get(s.patient).nom(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> patients.get(s.patient).prenom(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(s -> s.date)
                        .thenComparing(s -> s.id))
                .map(s -> new SeanceFacturable(s.id, patients.get(s.patient), s.date, new StatutDeSeance(s.statut),
                        PolitiqueDeForfaitApplique.pour(s.date, Optional.ofNullable(s.surcharge), prisesEnChargeDe(centre, s.patient))))
                .toList();
    }

    private List<PriseEnChargeTarifee> prisesEnChargeDe(CentreId centre, UUID patient) {
        return prisesEnCharge.stream().filter(p -> p.patient.equals(patient)).map(p -> p.pec).toList();
    }

    // ---- liaison séance → facture

    @Override
    public void lier(CentreId centre, UUID facture, java.util.List<UUID> liees) {
        for (UUID seance : liees) {
            Ligne ligne = garde(centre, seance, l -> !l.facturee && ("VALIDEE".equals(l.statut) || "SIGNEE".equals(l.statut)),
                    "Une ou plusieurs seances ne sont plus eligibles a la validation");
            ligne.facturee = true;
            ligne.facture = facture;
            ligne.statut = "FACTUREE";
        }
    }

    public UUID factureDe(UUID seance) {
        return seances.get(seance).facture;
    }

    public String statutDe(UUID seance) {
        return seances.get(seance).statut;
    }

    // ---- forfaits

    @Override
    public Optional<ForfaitApplique> forfait(CentreId centre, UUID forfait) {
        return centre.valeur().equals(centresDesForfaits.get(forfait)) ? Optional.of(forfaits.get(forfait)) : Optional.empty();
    }

    // ---- séances modifiables

    @Override
    public Optional<SeanceDeFacturation> trouver(CentreId centre, UUID seance) {
        Ligne ligne = seances.get(seance);
        if (ligne == null || !ligne.centre.equals(centre)) {
            return Optional.empty();
        }
        return Optional.of(new SeanceDeFacturation(ligne.id, centre, ligne.patient, ligne.date, new StatutDeSeance(ligne.statut),
                ligne.facturee, ligne.statutAvant == null ? null : new StatutDeSeance(ligne.statutAvant), ligne.absence));
    }

    @Override
    public void exclure(CentreId centre, UUID seance, StatutDeSeance statutAvant) {
        Ligne ligne = garde(centre, seance, l -> !l.facturee && l.statut.equals(statutAvant.valeur()),
                "Seance introuvable ou non modifiable pour exclusion");
        ligne.statutAvant = ligne.statut;
        ligne.statut = "ABSENT";
        ligne.absence = null;
    }

    @Override
    public void lierALAbsence(CentreId centre, UUID seance, UUID absence) {
        Ligne ligne = garde(centre, seance, l -> "ABSENT".equals(l.statut) && l.statutAvant != null,
                "Seance introuvable ou non modifiable pour exclusion");
        ligne.absence = absence;
    }

    @Override
    public void reinclure(CentreId centre, UUID seance, StatutDeSeance statutRetabli) {
        Ligne ligne = garde(centre, seance, l -> !l.facturee && "ABSENT".equals(l.statut) && l.statutAvant != null,
                "Seance introuvable ou non reinclusable");
        ligne.statut = statutRetabli.valeur();
        ligne.statutAvant = null;
        ligne.absence = null;
    }

    @Override
    public void poserLeForfait(CentreId centre, UUID seance, ForfaitApplique forfait, String auteur, Instant le) {
        Ligne ligne = garde(centre, seance, l -> !l.facturee && ("VALIDEE".equals(l.statut) || "SIGNEE".equals(l.statut)),
                "Seance introuvable ou non modifiable pour changement forfait");
        ligne.surcharge = forfait;
        ligne.auteurDeLaSurcharge = auteur;
    }

    public String auteurDeLaSurcharge(UUID seance) {
        return seances.get(seance).auteurDeLaSurcharge;
    }

    private Ligne garde(CentreId centre, UUID seance, java.util.function.Predicate<Ligne> etatAttendu, String message) {
        Ligne ligne = seances.get(seance);
        if (ligne == null || !ligne.centre.equals(centre) || !etatAttendu.test(ligne)) {
            throw new IllegalStateException(message);
        }
        return ligne;
    }

    private static final class Ligne {
        final UUID id;
        final CentreId centre;
        final UUID patient;
        final LocalDate date;
        String statut;
        boolean facturee;
        UUID facture;
        ForfaitApplique surcharge;
        String auteurDeLaSurcharge;
        String statutAvant;
        UUID absence;

        Ligne(UUID id, CentreId centre, UUID patient, LocalDate date, String statut, boolean facturee) {
            this.id = id;
            this.centre = centre;
            this.patient = patient;
            this.date = date;
            this.statut = statut;
            this.facturee = facturee;
        }
    }

    private record PriseEnChargeDuPatient(UUID patient, PriseEnChargeTarifee pec) {
    }
}
