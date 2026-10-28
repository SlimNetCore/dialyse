package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.application.LectureDesRessources;
import com.hemodialyse.backend.contexts.planification.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.planification.application.NotificationsDuPlacement;
import com.hemodialyse.backend.contexts.planification.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.planification.application.RisquesInfectieux;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Occupation;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre.Changement;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Double en memoire du placement : un seul « monde » (un centre, ses fiches, ses ressources, ses alertes) implemente tous les ports
 * du cas d'usage. La transaction est simulee : une exception restaure les fiches telles qu'elles etaient a l'entree de la
 * transaction englobante (la serologie de l'appelant et la reaffectation reussissent ou echouent ensemble).
 */
public final class PlacesEnMemoire implements PlacesDuCentreRepository, LectureDesRessources, RisquesInfectieux, NomsDesPatients,
        NotificationsDuPlacement, VerrouDesPlaces, UniteDeTravail, HorlogeDesCentres, PatientsDuCentre {

    public RessourcesDuCentre ressources;
    public LocalDate aujourdhui = LocalDate.of(2026, 10, 27);
    public final Map<PatientId, Occupation> fiches = new LinkedHashMap<>();
    public final Set<PatientId> sortis = new HashSet<>();
    public final Set<PatientId> serologiePositive = new HashSet<>();
    public final Map<PatientId, String> noms = new HashMap<>();
    public final Set<PatientId> autreCentre = new HashSet<>();
    public final List<String> alertes = new ArrayList<>();
    public final List<CentreId> verrous = new ArrayList<>();
    public int transactions;
    public int ecritures;
    public boolean verrouRefuse;
    private final Set<String> cles = new HashSet<>();
    private int profondeur;

    public PlacesEnMemoire(RessourcesDuCentre ressources) {
        this.ressources = ressources;
    }

    public PlacesEnMemoire patient(PatientId patient, String nom, Occupation occupation) {
        noms.put(patient, nom);
        fiches.put(patient, occupation == null ? new Occupation(patient, null, null, null, null, null) : occupation);
        return this;
    }

    private CategorieDIsolement categorie(PatientId patient) {
        return CategorieDIsolement.deduite(serologiePositive.contains(patient));
    }

    private Occupation avecCategorie(Occupation o) {
        return new Occupation(o.patient(), o.salle(), o.creneau(), o.generateur(), o.jours(), categorie(o.patient()));
    }

    // ── UniteDeTravail ──
    @Override
    public <T> T executer(Supplier<T> action) {
        transactions++;
        if (profondeur > 0) return action.get();
        Map<PatientId, Occupation> avant = new LinkedHashMap<>(fiches);
        int alertesAvant = alertes.size();
        Set<String> clesAvant = new HashSet<>(cles);
        profondeur++;
        try {
            return action.get();
        } catch (RuntimeException e) {
            fiches.clear();
            fiches.putAll(avant);
            while (alertes.size() > alertesAvant) alertes.removeLast();
            cles.clear();
            cles.addAll(clesAvant);
            throw e;
        } finally {
            profondeur--;
        }
    }

    // ── VerrouDesPlaces ──
    @Override
    public void prendre(CentreId centre) {
        if (verrouRefuse) throw new EcritureConcurrente();
        verrous.add(centre);
    }

    // ── PlacesDuCentreRepository ──
    @Override
    public PlacesDuCentre charger(CentreId centre, RessourcesDuCentre ressourcesLues, LocalDate jugeLe) {
        Collection<Occupation> actives = fiches.values().stream()
                .filter(o -> !sortis.contains(o.patient()) && !autreCentre.contains(o.patient()) && !o.estVide())
                .map(this::avecCategorie).toList();
        return PlacesDuCentre.de(centre, ressourcesLues, actives);
    }

    @Override
    public Optional<Occupation> placementActuel(CentreId centre, PatientId patient) {
        if (autreCentre.contains(patient) || !fiches.containsKey(patient)) return Optional.empty();
        return Optional.of(avecCategorie(fiches.get(patient)));
    }

    @Override
    public boolean estSorti(CentreId centre, PatientId patient, LocalDate jugeLe, String etatAVenir, LocalDate dateAVenir) {
        // l'état à venir « PERMANENT » est une reprise ; un état de sortie saisi avec une date passée est un sorti ; sinon l'état enregistré
        if ("PERMANENT".equals(etatAVenir)) return false;
        if (etatAVenir != null && java.util.Set.of("TRANSFERE", "DECEDE", "GREFFE", "GUERRI").contains(etatAVenir)) {
            return !autreCentre.contains(patient) && fiches.containsKey(patient);
        }
        return sortis.contains(patient) && !autreCentre.contains(patient);
    }

    @Override
    public List<PatientId> patientsDuGenerateur(CentreId centre, GenerateurId generateur, LocalDate jugeLe) {
        return fiches.values().stream()
                .filter(o -> generateur.equals(o.generateur()) && !sortis.contains(o.patient()) && !autreCentre.contains(o.patient()))
                .map(Occupation::patient).sorted(java.util.Comparator.comparing(p -> p.valeur().toString())).toList();
    }

    @Override
    public void enregistrer(PlacesDuCentre places) {
        for (Changement c : places.changements()) {
            ecritures++;
            Optional<Place> place = c.place();
            fiches.put(c.patient(), place.map(p -> new Occupation(c.patient(), p.salle(), p.creneau(), p.generateur(), p.jours(),
                    categorie(c.patient()))).orElseGet(() -> new Occupation(c.patient(), null, null, null, null, null)));
        }
    }

    // ── Ports de lecture ──
    @Override
    public RessourcesDuCentre duCentre(CentreId centre, LocalDate aPartirDu) {
        return ressources;
    }

    @Override
    public CategorieDIsolement de(CentreId centre, PatientId patient) {
        return autreCentre.contains(patient) ? CategorieDIsolement.SANS_RISQUE : categorie(patient);
    }

    @Override
    public Map<PatientId, String> de(CentreId centre, Collection<PatientId> patients) {
        Map<PatientId, String> trouves = new HashMap<>();
        for (PatientId p : patients) if (noms.containsKey(p) && !autreCentre.contains(p)) trouves.put(p, noms.get(p));
        return trouves;
    }

    @Override
    public LocalDate aujourdhui(CentreId centre) {
        return aujourdhui;
    }

    @Override
    public LocalDate jourDe(CentreId centre, java.time.Instant instant) {
        return instant.atZone(java.time.ZoneId.of("Africa/Algiers")).toLocalDate();
    }

    @Override
    public Situation situation(CentreId centre, PatientId patient) {
        if (autreCentre.contains(patient) || !fiches.containsKey(patient)) return Situation.INTROUVABLE;
        return sortis.contains(patient) ? Situation.SORTI : Situation.EN_SUIVI;
    }

    // ── NotificationsDuPlacement ──
    @Override
    public boolean patientReplaceEnIsolement(CentreId centre, PatientId patient, String nom, String salle, LocalDate jour) {
        alertes.add("REPLACE_ISOLEMENT|" + nom + "|" + salle);
        return true;
    }

    @Override
    public boolean isolementImpossible(CentreId centre, PatientId patient, String nom, LocalDate jour) {
        if (!cles.add("ISOLEMENT_IMPOSSIBLE|" + patient.valeur() + "|" + jour)) return false;
        alertes.add("ISOLEMENT_IMPOSSIBLE|" + nom);
        return true;
    }

    @Override
    public boolean retourEnSalleOrdinairePropose(CentreId centre, PatientId patient, String nom, String salle, LocalDate jour) {
        if (!cles.add("RETOUR|" + patient.valeur() + "|" + jour)) return false;
        alertes.add("RETOUR_PROPOSE|" + nom + "|" + salle);
        return true;
    }

    @Override
    public boolean generateurIndisponible(CentreId centre, GenerateurId generateur, String code, String statut, int nombre,
                                          List<String> cites, LocalDate jour) {
        if (!cles.add("GENERATEUR|" + generateur.valeur() + "|" + jour)) return false;
        alertes.add("GENERATEUR_INDISPONIBLE|" + code + "|" + statut + "|" + nombre + "|" + String.join(", ", cites));
        return true;
    }
}
