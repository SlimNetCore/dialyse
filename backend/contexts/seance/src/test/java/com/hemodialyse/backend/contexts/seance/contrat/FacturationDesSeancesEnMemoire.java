package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Double en mémoire des lectures et écritures publiées de la Séance pour la Facturation : mêmes gardes que l'adapter JDBC (ADR 0097). */
class FacturationDesSeancesEnMemoire implements SeancesAFacturerPubliees, ExclusionsEtForfaitsDesSeancesPublies, SeancesDesFacturesPubliees {

    private record Patient(UUID id, UUID centre, String code, String nom, String prenom, String etat, UUID centrePayeur, UUID agence) {
    }

    private static final class Seance {
        UUID id;
        UUID centre;
        UUID patient;
        LocalDate date;
        String statut;
        UUID facture;
        UUID forfaitId;
        String forfaitNom;
        BigDecimal forfaitPrix;
        String forfaitCode;
        String memoire;
        UUID absence;
    }

    private final Map<UUID, Patient> patients = new HashMap<>();
    private final Map<UUID, Seance> seances = new LinkedHashMap<>();

    void patient(UUID id, UUID centre, String code, String nom, String prenom, String etat) {
        patients.put(id, new Patient(id, centre, code, nom, prenom, etat, null, null));
    }

    void payeur(UUID patient, UUID centrePayeur, UUID agence) {
        Patient p = patients.get(patient);
        patients.put(patient, new Patient(p.id(), p.centre(), p.code(), p.nom(), p.prenom(), p.etat(), centrePayeur, agence));
    }

    void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, UUID facture) {
        Seance s = new Seance();
        s.id = id;
        s.centre = centre;
        s.patient = patient;
        s.date = date;
        s.statut = statut;
        s.facture = facture;
        seances.put(id, s);
    }

    void surcharge(UUID seance, UUID forfait, String nom, BigDecimal prix, String code) {
        Seance s = seances.get(seance);
        s.forfaitId = forfait;
        s.forfaitNom = nom;
        s.forfaitPrix = prix;
        s.forfaitCode = code;
    }

    void memoire(UUID seance, String statutAvant) {
        seances.get(seance).memoire = statutAvant;
    }

    String statutDe(UUID seance) {
        return seances.get(seance).statut;
    }

    String codeDuForfaitDe(UUID seance) {
        return seances.get(seance).forfaitCode;
    }

    String memoireDe(UUID seance) {
        return seances.get(seance).memoire;
    }

    UUID absenceDe(UUID seance) {
        return seances.get(seance).absence;
    }

    private Optional<Seance> du(UUID centre, UUID id) {
        return Optional.ofNullable(seances.get(id)).filter(s -> s.centre.equals(centre));
    }

    private static void exigerUneLigne(boolean modifiee, String message) {
        if (!modifiee) {
            throw new IllegalStateException(message);
        }
    }

    // ---- séances à facturer

    @Override
    public List<SeanceAFacturerPubliee> dansLaPeriode(UUID centre, LocalDate debut, LocalDate fin) {
        return seances.values().stream()
                .filter(s -> s.centre.equals(centre) && !s.date.isBefore(debut) && !s.date.isAfter(fin))
                .filter(s -> ("VALIDEE".equals(s.statut) || "SIGNEE".equals(s.statut)) && s.facture == null)
                .filter(s -> patients.containsKey(s.patient) && patients.get(s.patient).centre().equals(s.centre))
                .sorted(Comparator.<Seance, String>comparing(s -> patients.get(s.patient).nom())
                        .thenComparing(s -> patients.get(s.patient).prenom()).thenComparing(s -> s.date).thenComparing(s -> s.id))
                .map(s -> {
                    Patient p = patients.get(s.patient);
                    return new SeanceAFacturerPubliee(s.id, s.date, s.statut, s.forfaitId, s.forfaitNom, s.forfaitPrix,
                            new PatientDeLaSeancePublie(p.id(), p.code(), p.nom(), p.prenom(), p.etat(), "ASS-" + p.code(),
                                    p.centrePayeur(), p.agence()));
                }).toList();
    }

    // ---- exclusion, réinclusion, forfait imposé

    @Override
    public Optional<SeanceExcluablePublie> trouver(UUID centre, UUID seance) {
        return du(centre, seance).map(s -> new SeanceExcluablePublie(s.id, s.patient, s.date, s.statut, s.facture != null,
                s.memoire == null || s.memoire.isBlank() ? null : s.memoire, s.absence));
    }

    @Override
    public void exclure(UUID centre, UUID seance, String statutAvant) {
        Optional<Seance> s = du(centre, seance).filter(x -> x.facture == null && x.statut.equals(statutAvant));
        exigerUneLigne(s.isPresent(), "Seance introuvable ou non modifiable pour exclusion");
        s.get().statut = "ABSENT";
        s.get().memoire = statutAvant;
        s.get().absence = null;
    }

    @Override
    public void lierALAbsence(UUID centre, UUID seance, UUID absence) {
        Optional<Seance> s = du(centre, seance).filter(x -> "ABSENT".equals(x.statut) && x.memoire != null);
        exigerUneLigne(s.isPresent(), "Seance introuvable ou non modifiable pour exclusion");
        s.get().absence = absence;
    }

    @Override
    public void reinclure(UUID centre, UUID seance, String statutRetabli) {
        Optional<Seance> s = du(centre, seance).filter(x -> x.facture == null && "ABSENT".equals(x.statut) && x.memoire != null);
        exigerUneLigne(s.isPresent(), "Seance introuvable ou non reinclusable");
        s.get().statut = statutRetabli;
        s.get().memoire = null;
        s.get().absence = null;
    }

    @Override
    public void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le) {
        Optional<Seance> s = du(centre, seance)
                .filter(x -> x.facture == null && ("VALIDEE".equals(x.statut) || "SIGNEE".equals(x.statut)));
        exigerUneLigne(s.isPresent(), "Seance introuvable ou non modifiable pour changement forfait");
        s.get().forfaitId = forfait;
        s.get().forfaitNom = libelle;
        s.get().forfaitPrix = prixHt;
    }

    // ---- séances facturées

    @Override
    public Map<UUID, Long> nombreParFacture(UUID centre, Collection<UUID> factures) {
        Map<UUID, Long> nombres = new HashMap<>();
        seances.values().stream().filter(s -> s.centre.equals(centre) && s.facture != null && factures.contains(s.facture))
                .forEach(s -> nombres.merge(s.facture, 1L, Long::sum));
        return nombres;
    }

    @Override
    public long nombreDeSeancesFactureesEntre(UUID centre, LocalDate debut, LocalDate fin) {
        return seances.values().stream().filter(s -> s.centre.equals(centre) && "FACTUREE".equals(s.statut)
                && !s.date.isBefore(debut) && !s.date.isAfter(fin)).count();
    }
}
