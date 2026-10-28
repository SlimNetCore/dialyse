package com.hemodialyse.backend.contexts.planification.domain;

import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.IsolementImpossible;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PatientPlace;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PatientReplaceEnIsolement;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PlaceRetiree;
import com.hemodialyse.backend.contexts.planification.domain.EvenementDePlacement.PlacementModifie;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.CaseDeGrille;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.DemandeDePlace;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Proposition;
import com.hemodialyse.backend.contexts.planification.domain.PropositionsDePlace.Resultat;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre.Salle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les places d'un centre (racine, une instance par centre, volumétrie de l'ordre de 300 places). Garantit que chaque patient
 * placé a une place cohérente : <b>un générateur ne sert qu'un patient par jour et par créneau</b> (RG-PLN-021), <b>jamais de
 * mélange risque / sans risque sur un générateur</b> (RG-PLN-024), salle, créneau et générateur du centre, générateur en service
 * rattaché à la salle, jours d'ouverture (RG-PLN-011, 023, 050).
 * <p>
 * Le patient (identité, état, dates) et ses sérologies ne sont pas dans l'agrégat : la catégorie d'isolement et les
 * {@link RessourcesDuCentre} sont reçues en entrée. Les écritures ne sont sûres que sous le {@link VerrouDesPlaces}.
 * Les changements sont accumulés ({@link #changements()}) puis écrits par le dépôt.
 */
public final class PlacesDuCentre {

    /** Règle de placement enfreinte ; le code stable est {@code PLACEMENT_<nom>} (RG-PLN-050). */
    public enum Violation {
        SALLE_INCONNUE,
        CRENEAU_INCONNU,
        JOUR_FERME,
        ISOLEMENT_REQUIS,
        /** Patient sans risque en salle d'isolement : refusé (décision du 27/10, KB-PLN-55). */
        ISOLEMENT_RESERVE,
        GENERATEUR_INCONNU,
        GENERATEUR_INCOMPATIBLE,
        GENERATEUR_OCCUPE
    }

    public enum IssueDeReaffectation {AUCUN_RISQUE, NON_PLACE, DEJA_CONFORME, DEPLACE, A_REPLANIFIER}

    /** @param proposition la place prise (DEPLACE) ; nul sinon */
    public record ResultatDeReaffectation(IssueDeReaffectation issue, Proposition proposition) {
    }

    public enum IssueDeRetour {NON_CONCERNE, PROPOSE, AUCUNE_PLACE}

    /** Retour en salle ordinaire d'un patient redevenu sans risque : une proposition, jamais appliquée par le domaine. */
    public record RetourEnSalleOrdinaire(IssueDeRetour issue, Proposition proposition) {
    }

    /** Écriture à faire : la nouvelle place du patient, ou aucune (colonnes de place effacées). */
    public record Changement(PatientId patient, Optional<Place> place) {
    }

    /** Capacité hebdomadaire : places = générateurs en service x créneaux x jours d'ouverture. */
    public record Capacite(int generateursEnService, int creneaux, int joursOuverts, int places, int occupees) {
        public int libres() {
            return Math.max(0, places - occupees);
        }
    }

    public static final int NOMBRE_DE_PROPOSITIONS_DE_REAFFECTATION = 5;

    private final CentreId centre;
    private final RessourcesDuCentre ressources;
    private final Map<PatientId, Occupation> occupations = new LinkedHashMap<>();
    private final Map<PatientId, Optional<Place>> changements = new LinkedHashMap<>();
    private final List<EvenementDePlacement> evenements = new ArrayList<>();
    private final java.util.Set<PatientId> sortis = new java.util.HashSet<>();

    private PlacesDuCentre(CentreId centre, RessourcesDuCentre ressources) {
        if (centre == null) throw new SaisieInvalide("Centre requis");
        if (ressources == null) throw new SaisieInvalide("Ressources du centre requises");
        this.centre = centre;
        this.ressources = ressources;
    }

    public static PlacesDuCentre de(CentreId centre, RessourcesDuCentre ressources, Collection<Occupation> occupations) {
        PlacesDuCentre places = new PlacesDuCentre(centre, ressources);
        for (Occupation o : occupations) places.occupations.put(o.patient(), o);
        return places;
    }

    public CentreId centre() {
        return centre;
    }

    public RessourcesDuCentre ressources() {
        return ressources;
    }

    public Collection<Occupation> occupations() {
        return List.copyOf(occupations.values());
    }

    public Optional<Occupation> occupationDe(PatientId patient) {
        return Optional.ofNullable(occupations.get(patient));
    }

    /**
     * Retient le placement actuel d'un patient même s'il n'est plus actif (patient sorti dont la fiche garde ses colonnes) :
     * nécessaire à la règle « placement inchangé = accepté » (RG-PLN-051). Sans effet s'il est déjà connu.
     */
    public void connaitre(Occupation occupation) {
        occupations.putIfAbsent(occupation.patient(), occupation);
    }

    /**
     * Retient qu'un patient est SORTI (dernier jour de place dépassé) : une nouvelle place lui est refusée
     * ({@code PLACEMENT_PATIENT_SORTI}, KB-PLN-83) ; sa place inchangée reste acceptée (RG-PLN-051).
     */
    public void connaitreUnSorti(PatientId patient) {
        sortis.add(patient);
    }

    public List<Changement> changements() {
        List<Changement> liste = new ArrayList<>();
        changements.forEach((patient, place) -> liste.add(new Changement(patient, place)));
        return List.copyOf(liste);
    }

    public List<EvenementDePlacement> evenements() {
        return List.copyOf(evenements);
    }

    /** Les places telles qu'elles seraient sans ce patient (sa place actuelle est libérée pour le calcul, RG-PLN-034). Lecture seule. */
    public PlacesDuCentre sans(PatientId patient) {
        PlacesDuCentre copie = new PlacesDuCentre(centre, ressources);
        occupations.forEach((id, o) -> {
            if (!id.equals(patient)) copie.occupations.put(id, o);
        });
        return copie;
    }

    // ───────────────────────────── Lectures ─────────────────────────────

    public Resultat proposer(DemandeDePlace demande, int limite) {
        return PropositionsDePlace.rechercher(ressources, occupations.values(), demande, limite);
    }

    public List<CaseDeGrille> grille() {
        return PropositionsDePlace.grille(ressources, occupations.values());
    }

    public Capacite capacite() {
        int generateurs = ressources.generateurs().size();
        int occupees = grille().stream().mapToInt(CaseDeGrille::occupes).sum();
        int ouverts = ressources.joursOuverts().size();
        return new Capacite(generateurs, ressources.creneaux().size(), ouverts,
                generateurs * ressources.creneaux().size() * ouverts, occupees);
    }

    // ───────────────────────────── Placement ─────────────────────────────

    /** Contrôle une place demandée à la création d'une fiche (pas encore de patient), sans rien écrire. */
    public void verifierPourUneCreation(Optional<Place> demandee, CategorieDIsolement categorie) {
        demandee.ifPresent(place -> controler(new IndexDOccupation(ressources, occupations.values()), categorie, place));
    }

    /** Contrôle sans écrire (même règles que {@link #placer}). */
    public void verifier(PatientId patient, Optional<Place> demandee, CategorieDIsolement categorie) {
        controlerSiChange(patient, demandee, categorie);
    }

    /**
     * Place le patient, ou lui retire sa place. Une fiche sans aucune place est acceptée ; un placement <b>inchangé</b> est
     * accepté sans contrôle (une anomalie héritée ne bloque pas une autre modification) ; sinon le premier manquement refuse
     * (RG-PLN-050, 051).
     *
     * @throws RegleDePlanificationViolee {@code PLACEMENT_<violation>}
     * @return l'événement levé, ou vide si rien n'a changé
     */
    public Optional<EvenementDePlacement> placer(PatientId patient, Optional<Place> demandee, CategorieDIsolement categorie) {
        Occupation actuelle = occupations.get(patient);
        if (demandee.isEmpty()) {
            if (actuelle == null || actuelle.estVide()) return Optional.empty();
            occupations.put(patient, new Occupation(patient, null, null, null, null, actuelle.categorie()));
            changements.put(patient, Optional.empty());
            return lever(new PlaceRetiree(patient, actuelle));
        }
        if (!controlerSiChange(patient, demandee, categorie)) return Optional.empty();
        Place place = demandee.get();
        occupations.put(patient, new Occupation(patient, place.salle(), place.creneau(), place.generateur(), place.jours(), categorie));
        changements.put(patient, Optional.of(place));
        return lever(actuelle == null || actuelle.estVide() ? new PatientPlace(patient, place)
                : new PlacementModifie(patient, actuelle, place));
    }

    /** @return faux si le placement est inchangé (aucun contrôle) */
    private boolean controlerSiChange(PatientId patient, Optional<Place> demandee, CategorieDIsolement categorie) {
        if (demandee.isEmpty()) return true;
        Occupation actuelle = occupations.get(patient);
        if (actuelle != null && actuelle.correspondA(demandee.get())) return false;
        if (sortis.contains(patient)) {
            throw new RegleDePlanificationViolee("PLACEMENT_PATIENT_SORTI",
                    "Placement refusé : le patient est sorti (transfert, guérison, décès, greffe ou séjour terminé)");
        }
        controler(new IndexDOccupation(ressources, sans(patient).occupations.values()), categorie, demandee.get());
        return true;
    }

    private void controler(IndexDOccupation autres, CategorieDIsolement categorie, Place place) {
        List<Violation> violations = violations(autres, categorie.aRisque(), place);
        if (!violations.isEmpty()) {
            throw new RegleDePlanificationViolee("PLACEMENT_" + violations.getFirst().name(),
                    "Placement refusé par la planification : " + violations);
        }
    }

    private List<Violation> violations(IndexDOccupation autres, boolean aRisque, Place place) {
        List<Violation> violations = new ArrayList<>();
        if (!ressources.contientSalle(place.salle())) violations.add(Violation.SALLE_INCONNUE);
        if (!ressources.contientCreneau(place.creneau())) violations.add(Violation.CRENEAU_INCONNU);
        if (!ressources.joursOuverts().containsAll(place.jours())) violations.add(Violation.JOUR_FERME);
        boolean isolement = autres.estIsolement(place.salle());
        if (aRisque && !isolement) violations.add(Violation.ISOLEMENT_REQUIS);
        if (!aRisque && isolement) violations.add(Violation.ISOLEMENT_RESERVE);
        RessourcesDuCentre.Generateur generateur = autres.generateursDe(place.salle()).stream()
                .filter(g -> g.id().equals(place.generateur())).findFirst().orElse(null);
        if (generateur == null) {
            violations.add(Violation.GENERATEUR_INCONNU);
            return violations;
        }
        if (!autres.generateurCompatible(generateur, aRisque || isolement)) violations.add(Violation.GENERATEUR_INCOMPATIBLE);
        if (place.jours().stream().anyMatch(j -> !autres.generateurLibre(generateur, place.creneau(), j))) {
            violations.add(Violation.GENERATEUR_OCCUPE);
        }
        return violations;
    }

    /**
     * Les règles qu'enfreindrait cette place pour un patient de cette catégorie face aux occupations ACTUELLES de ce centre (le
     * patient lui-même doit en être absent : voir {@link #sans}). Lecture seule ; sert aux propositions d'optimisation.
     */
    public List<Violation> violationsDe(Place place, CategorieDIsolement categorie) {
        return violations(new IndexDOccupation(ressources, occupations.values()), categorie.aRisque(), place);
    }

    /**
     * Replace d'un coup les patients d'une proposition d'optimisation (P22) : <b>tout ou rien</b>. Chaque nouvelle place est jugée
     * face à l'état FINAL de tous les patients (les échanges de places entre deux patients sont donc possibles) ; si l'une enfreint
     * une règle rien n'est enregistré. Les jours de la place sont écrits avec elle.
     *
     * @param nouvelles  la nouvelle place de chaque patient déplacé (ordre conservé : c'est celui du premier refus)
     * @param categories catégorie d'isolement de chaque patient déplacé
     * @throws RegleDePlanificationViolee {@code PLACEMENT_<violation>} de la première place refusée
     */
    public void replacerLesPatients(Map<PatientId, Place> nouvelles, Map<PatientId, CategorieDIsolement> categories) {
        Map<PatientId, Occupation> finales = new LinkedHashMap<>(occupations);
        nouvelles.forEach((patient, place) -> finales.put(patient, new Occupation(patient, place.salle(), place.creneau(),
                place.generateur(), place.jours(), categories.getOrDefault(patient, CategorieDIsolement.SANS_RISQUE))));
        for (Map.Entry<PatientId, Place> n : nouvelles.entrySet()) {
            List<Occupation> autres = finales.values().stream().filter(o -> !o.patient().equals(n.getKey())).toList();
            controler(new IndexDOccupation(ressources, autres), categories.getOrDefault(n.getKey(), CategorieDIsolement.SANS_RISQUE),
                    n.getValue());
        }
        nouvelles.forEach((patient, place) -> {
            Occupation actuelle = occupations.get(patient);
            occupations.put(patient, finales.get(patient));
            changements.put(patient, Optional.of(place));
            lever(actuelle == null || actuelle.estVide() ? new PatientPlace(patient, place)
                    : new PlacementModifie(patient, actuelle, place));
        });
    }

    // ───────────────────────────── Isolement ─────────────────────────────

    /**
     * Patient devenu à risque (RG-PLN-053) : replacé en salle d'isolement avec les mêmes jours, le créneau conservé si possible
     * (meilleure des {@value #NOMBRE_DE_PROPOSITIONS_DE_REAFFECTATION} propositions) ; sans place d'isolement il <b>reste en place</b>
     * et l'événement {@link IsolementImpossible} est levé.
     */
    public ResultatDeReaffectation replacerEnIsolement(PatientId patient, CategorieDIsolement categorie) {
        if (!categorie.aRisque()) return new ResultatDeReaffectation(IssueDeReaffectation.AUCUN_RISQUE, null);
        Occupation actuelle = occupations.get(patient);
        if (actuelle == null || actuelle.estVide() || actuelle.salle() == null || actuelle.jours().isEmpty()) {
            return new ResultatDeReaffectation(IssueDeReaffectation.NON_PLACE, null);
        }
        PlacesDuCentre autres = sans(patient);
        IndexDOccupation index = new IndexDOccupation(ressources, autres.occupations.values());
        if (conforme(index, actuelle)) return new ResultatDeReaffectation(IssueDeReaffectation.DEJA_CONFORME, null);
        List<Proposition> propositions = PropositionsDePlace.rechercher(index,
                new DemandeDePlace(actuelle.jours().size(), actuelle.jours(), actuelle.creneau(), null, CategorieDIsolement.A_RISQUE),
                NOMBRE_DE_PROPOSITIONS_DE_REAFFECTATION).propositions();
        if (propositions.isEmpty()) {
            lever(new IsolementImpossible(patient));
            return new ResultatDeReaffectation(IssueDeReaffectation.A_REPLANIFIER, null);
        }
        Proposition meilleure = propositions.getFirst();
        Place place = new Place(meilleure.salle().id(), meilleure.creneau().id(), meilleure.generateur().id(), actuelle.jours());
        occupations.put(patient, new Occupation(patient, place.salle(), place.creneau(), place.generateur(), place.jours(),
                CategorieDIsolement.A_RISQUE));
        changements.put(patient, Optional.of(place));
        lever(new PatientReplaceEnIsolement(patient, actuelle, place, meilleure.salle()));
        return new ResultatDeReaffectation(IssueDeReaffectation.DEPLACE, meilleure);
    }

    private boolean conforme(IndexDOccupation autres, Occupation actuelle) {
        if (!actuelle.estComplete()) return false;
        Place place = new Place(actuelle.salle(), actuelle.creneau(), actuelle.generateur(), actuelle.jours());
        return violations(autres, true, place).isEmpty();
    }

    /**
     * Patient redevenu sans risque (sérologie négative) encore placé en salle d'isolement : propose une place en salle ordinaire
     * (mêmes jours, créneau conservé si possible). <b>Ne déplace jamais</b> (décision du 27/10, KB-PLN-61).
     */
    public RetourEnSalleOrdinaire proposerLeRetourEnSalleOrdinaire(PatientId patient, CategorieDIsolement categorie) {
        Occupation actuelle = occupations.get(patient);
        if (categorie.aRisque() || actuelle == null || actuelle.salle() == null || actuelle.jours().isEmpty()
                || !ressources.estIsolement(actuelle.salle())) {
            return new RetourEnSalleOrdinaire(IssueDeRetour.NON_CONCERNE, null);
        }
        List<Proposition> propositions = PropositionsDePlace.rechercher(
                new IndexDOccupation(ressources, sans(patient).occupations.values()),
                new DemandeDePlace(actuelle.jours().size(), actuelle.jours(), actuelle.creneau(), null,
                        CategorieDIsolement.SANS_RISQUE), NOMBRE_DE_PROPOSITIONS_DE_REAFFECTATION).propositions();
        return propositions.isEmpty() ? new RetourEnSalleOrdinaire(IssueDeRetour.AUCUNE_PLACE, null)
                : new RetourEnSalleOrdinaire(IssueDeRetour.PROPOSE, propositions.getFirst());
    }

    private Optional<EvenementDePlacement> lever(EvenementDePlacement evenement) {
        evenements.add(evenement);
        return Optional.of(evenement);
    }

    /** Salle du centre par identité, pour nommer une alerte. */
    public Optional<Salle> salle(Identifiants.SalleId salle) {
        return ressources.salles().stream().filter(s -> s.id().equals(salle)).findFirst();
    }
}
