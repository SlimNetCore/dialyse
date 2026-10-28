package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CategorieDIsolement;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementTemporaire;
import com.hemodialyse.backend.contexts.planification.domain.DeplacementsTemporairesRepository;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.DonneesDOptimisation.PatientAPlacer;
import com.hemodialyse.backend.contexts.planification.domain.Empreinte;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionsRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Perimetre;
import com.hemodialyse.backend.contexts.planification.domain.Place;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.PlacesDuCentreRepository;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AbsenceConnue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.AffectationPrevue;
import com.hemodialyse.backend.contexts.planification.domain.Presence.InfirmierDeLaPresence;
import com.hemodialyse.backend.contexts.planification.domain.Presence.Personnel;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import com.hemodialyse.backend.contexts.planification.domain.RemplacantEligible;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier.CaseVoulue;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier.Changements;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDesPlaces;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CachesDesPatients;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Applique une proposition d'optimisation validée par l'administration (P22, RG-PLN-091 à 094) : places des patients, roulement des
 * infirmiers, remplacements ou déplacements temporaires selon le périmètre. <b>Tout ou rien, UNE transaction</b> (exception décidée à
 * la règle « une transaction = un agrégat », ADR 0051), et chaque écriture passe par son agrégat : {@link PlacesDuCentre} juge les
 * nouvelles places face à l'état final, {@link RoulementDeLInfirmier} refuse un roulement qui mettrait un infirmier dans deux salles
 * au même créneau, {@link Remplacement} relie le remplacé, {@link DeplacementTemporaire} refuse une cible incohérente.
 * <p>
 * Verrous, dans l'ordre global (ADR 0051, 0056, 0057) : le centre, puis les fiches des infirmiers par identifiant croissant. L'état
 * du centre est relu SOUS ces verrous et son empreinte comparée à celle du calcul ({@code OPTIMISATION_PERIMEE}, 409).
 * Les infirmiers dont le roulement ou les remplacements changent sont notifiés (clé déterministe).
 */
public class AppliquerUneProposition {

    private final UniteDeTravail uniteDeTravail;
    private final VerrouDesPlaces verrou;
    private final VerrouillerLesFichesDesInfirmiers verrouDesFiches;
    private final ExecutionsRepository executions;
    private final DonneesDeLOptimisation donnees;
    private final PlacesDuCentreRepository places;
    private final HorlogeDesCentres horloge;
    private final RoulementsRepository roulements;
    private final RemplacementsRepository remplacements;
    private final DeplacementsTemporairesRepository deplacements;
    private final NouveauxIdentifiantsDeLOptimisation identifiants;
    private final NotificationsDeLOptimisation notifications;
    private final CachesDesPatients caches;
    private final Clock instant;

    @SuppressWarnings("java:S107")
    public AppliquerUneProposition(UniteDeTravail uniteDeTravail, VerrouDesPlaces verrou,
                                   VerrouillerLesFichesDesInfirmiers verrouDesFiches, ExecutionsRepository executions,
                                   DonneesDeLOptimisation donnees, PlacesDuCentreRepository places, HorlogeDesCentres horloge,
                                   RoulementsRepository roulements, RemplacementsRepository remplacements,
                                   DeplacementsTemporairesRepository deplacements, NouveauxIdentifiantsDeLOptimisation identifiants,
                                   NotificationsDeLOptimisation notifications, CachesDesPatients caches, Clock instant) {
        this.uniteDeTravail = uniteDeTravail;
        this.verrou = verrou;
        this.verrouDesFiches = verrouDesFiches;
        this.executions = executions;
        this.donnees = donnees;
        this.places = places;
        this.horloge = horloge;
        this.roulements = roulements;
        this.remplacements = remplacements;
        this.deplacements = deplacements;
        this.identifiants = identifiants;
        this.notifications = notifications;
        this.caches = caches;
        this.instant = instant;
    }

    /**
     * @throws RegleDePlanificationViolee {@code OPTIMISATION_INTROUVABLE}, {@code OPTIMISATION_NON_APPLICABLE},
     *                                    {@code OPTIMISATION_DEJA_APPLIQUEE}, {@code OPTIMISATION_PERIMEE},
     *                                    {@code OPTIMISATION_PLACEMENT_<règle>}, {@code AFFECTATION_CHEVAUCHEMENT},
     *                                    {@code DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE}
     */
    public ExecutionDOptimisation executer(CentreId centre, ExecutionId id) {
        return uniteDeTravail.executer(() -> {
            verrou.prendre(centre);
            ExecutionDOptimisation execution = executions.parId(centre, id).orElseThrow(ConsulterLesOptimisations::introuvable);
            execution.exigerApplicable();
            ParametresDOptimisation parametres = execution.parametres();
            ResultatDOptimisation resultat = execution.resultat();
            if (parametres.perimetre().planifieLesInfirmiers()) {
                verrouDesFiches.executer(centre, infirmiersTouches(centre, parametres, resultat));
            }
            DonneesDOptimisation actuelles = donnees.charger(centre, parametres.debutSemaine(), parametres.finHorizon());
            ExecutionDOptimisation appliquee = execution.appliquer(Empreinte.calculer(actuelles, parametres), instant.instant());

            if (parametres.perimetre().placeLesPatients()) appliquerLesDeplacements(centre, actuelles, resultat.deplacements());
            if (parametres.perimetre().planifieLeRoulement()) {
                appliquerLeRoulement(centre, id, actuelles, resultat.vacations());
            } else if (parametres.perimetre() == Perimetre.COUVERTURE) {
                appliquerLaCouverture(centre, id, actuelles, resultat.vacations());
            } else if (parametres.perimetre() == Perimetre.MAINTENANCE) {
                appliquerLesTemporaires(centre, actuelles, resultat.temporaires());
            }
            executions.enregistrer(appliquee);
            signalerLesDeplacements(centre, parametres.perimetre(), resultat);
            return appliquee;
        });
    }

    /** Les fiches à verrouiller : tout le personnel actif pour un roulement, les seuls remplaçants proposés pour une couverture. */
    private Set<InfirmierId> infirmiersTouches(CentreId centre, ParametresDOptimisation parametres, ResultatDOptimisation resultat) {
        Set<InfirmierId> fiches = new LinkedHashSet<>();
        if (parametres.perimetre().planifieLeRoulement()) {
            donnees.charger(centre, parametres.debutSemaine(), parametres.finHorizon()).personnel().infirmiers()
                    .forEach(i -> fiches.add(new InfirmierId(i.id())));
        }
        resultat.vacations().stream().filter(v -> !v.existante() || parametres.perimetre().planifieLeRoulement())
                .forEach(v -> fiches.add(v.infirmier()));
        return fiches;
    }

    // ───────────────────────────── Places ─────────────────────────────

    private void appliquerLesDeplacements(CentreId centre, DonneesDOptimisation actuelles, List<DeplacementDePatient> deplaces) {
        if (deplaces.isEmpty()) return;
        Map<PatientId, PatientAPlacer> parPatient = new HashMap<>();
        actuelles.patients().forEach(p -> parPatient.put(p.patient(), p));
        Map<PatientId, Place> nouvelles = new LinkedHashMap<>();
        Map<PatientId, CategorieDIsolement> categories = new HashMap<>();
        try {
            for (DeplacementDePatient d : deplaces) {
                PatientAPlacer patient = parPatient.get(d.patient());
                if (patient == null)
                    throw new RegleDePlanificationViolee("PLACEMENT_INCOMPLET", "Placement refusé par la planification : [INCOMPLET]");
                Set<JourDeLaSemaine> jours = d.jours() == null ? patient.jours() : EnumSet.copyOf(d.jours());
                nouvelles.put(d.patient(), new Place(d.vers().salle(), d.vers().creneau(), d.vers().generateur(), jours));
                categories.put(d.patient(), patient.categorie());
            }
            LocalDate jour = horloge.aujourdhui(centre);
            PlacesDuCentre lues = places.charger(centre, actuelles.ressources(), jour);
            lues.replacerLesPatients(nouvelles, categories);
            places.enregistrer(lues);
        } catch (RegleDePlanificationViolee e) {
            if (e.code().startsWith("PLACEMENT_")) {
                // même libellé que le legacy : les règles enfreintes, entre crochets
                String regles = e.getMessage().substring(e.getMessage().lastIndexOf(": ") + 2);
                throw new RegleDePlanificationViolee("OPTIMISATION_" + e.code(),
                        "Une nouvelle place proposée enfreint les règles de la planification : [" + regles + "]");
            }
            throw e;
        }
        nouvelles.keySet().forEach(patient -> caches.evincer(centre.valeur(), patient.valeur()));
    }

    // ───────────────────────────── Roulement ─────────────────────────────

    /**
     * Remplace le roulement des infirmiers actifs par celui de la proposition : les affectations identiques sont gardées, les autres
     * modifiées, créées ou supprimées ; chaque roulement obtenu est jugé par son agrégat.
     */
    private void appliquerLeRoulement(CentreId centre, ExecutionId execution, DonneesDOptimisation actuelles,
                                      List<VacationPlanifiee> vacations) {
        Map<InfirmierId, Map<CaseVoulue, Set<JourDeLaSemaine>>> voulu = new LinkedHashMap<>();
        for (VacationPlanifiee v : vacations) {
            voulu.computeIfAbsent(v.infirmier(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(new CaseVoulue(v.salle(), v.creneau()), k -> EnumSet.noneOf(JourDeLaSemaine.class)).add(v.jour());
        }
        Set<InfirmierId> infirmiers = new LinkedHashSet<>();
        actuelles.personnel().infirmiers().forEach(i -> infirmiers.add(new InfirmierId(i.id())));
        infirmiers.addAll(voulu.keySet());
        for (InfirmierId infirmier : infirmiers.stream().sorted(Comparator.comparing(i -> i.valeur().toString())).toList()) {
            RoulementDeLInfirmier actuel = roulements.de(centre, infirmier);
            Changements changements = actuel.remplacerPar(voulu.getOrDefault(infirmier, Map.of()), identifiants::affectation);
            changements.retirees().forEach(a -> roulements.retirer(centre, infirmier, a));
            changements.modifiees().forEach(a -> roulements.remplacer(centre, infirmier, a));
            changements.ajoutees().forEach(a -> roulements.ajouter(centre, infirmier, a));
            if (!changements.estVide()) notifications.roulementModifie(centre, infirmier, execution);
        }
    }

    // ───────────────────────────── Couverture ─────────────────────────────

    /**
     * Crée les remplacements des vacations que le roulement ne couvrait pas (celles déjà tenues sont ignorées). Le remplacé est
     * relié : l'infirmier absent ce jour-là, normalement prévu sur la case, pas encore remplacé (décision du 28/10, KB-PLN-97).
     */
    private void appliquerLaCouverture(CentreId centre, ExecutionId execution, DonneesDOptimisation actuelles,
                                       List<VacationPlanifiee> vacations) {
        Personnel personnel = actuelles.personnel();
        Map<UUID, InfirmierDeLaPresence> fiches = new HashMap<>();
        personnel.infirmiers().forEach(i -> fiches.put(i.id(), i));
        Set<String> dejaRemplaces = new HashSet<>();
        for (VacationPlanifiee v : vacations) {
            if (v.existante()) continue;
            boolean salleDIsolement = actuelles.ressources().estIsolement(v.salle());
            InfirmierDeLaPresence fiche = fiches.get(v.infirmier().valeur());
            RemplacantEligible.Situation situation = new RemplacantEligible.Situation(v.infirmier(), fiche != null,
                    fiche != null && fiche.habiliteIsolement(), salleDIsolement, estAbsent(personnel, v.infirmier().valeur(), v.date()),
                    estDejaPrevu(personnel, v));
            RemplacantEligible eligible = RemplacantEligible.prouver(situation);
            InfirmierId remplace = absentPrevuSurLaCase(personnel, v, dejaRemplaces);
            remplacements.enregistrer(Remplacement.affecter(identifiants.remplacement(), centre, v.date(), v.salle(), v.creneau(),
                    eligible, remplace));
            notifications.remplacementAffecte(centre, v.infirmier(), v.date(), v.salle(), v.creneau(), execution);
        }
    }

    private static boolean estAbsent(Personnel personnel, UUID infirmier, LocalDate jour) {
        return personnel.absences().stream().anyMatch(a -> a.infirmier().equals(infirmier) && a.couvre(jour));
    }

    private static boolean estDejaPrevu(Personnel personnel, VacationPlanifiee v) {
        JourDeLaSemaine jour = v.jour();
        boolean auRoulement = personnel.affectations().stream().anyMatch(a -> a.infirmier().equals(v.infirmier().valeur())
                && a.creneau().equals(v.creneau().valeur()) && a.jours().contains(jour));
        boolean remplacant = personnel.remplacements().stream().anyMatch(r -> r.infirmier().equals(v.infirmier().valeur())
                && r.date().equals(v.date()) && r.creneau().equals(v.creneau().valeur()));
        return auRoulement || remplacant;
    }

    /** L'absent (ordre des identifiants) prévu sur la case au roulement qui n'a pas encore de remplaçant ; vide si personne. */
    private static InfirmierId absentPrevuSurLaCase(Personnel personnel, VacationPlanifiee v, Set<String> dejaRemplaces) {
        List<UUID> candidats = personnel.affectations().stream()
                .filter((AffectationPrevue a) -> a.salle().equals(v.salle().valeur()) && a.creneau().equals(v.creneau().valeur())
                        && a.jours().contains(v.jour()))
                .map(AffectationPrevue::infirmier)
                .filter(i -> estAbsent(personnel, i, v.date()))
                .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
        for (UUID candidat : candidats) {
            String cle = candidat + "|" + v.date() + "|" + v.creneau().valeur();
            if (dejaRemplaces.add(cle)) return new InfirmierId(candidat);
        }
        return null;
    }

    // ───────────────────────────── Maintenance ─────────────────────────────

    /** Enregistre les déplacements temporaires d'une séance datée ; la place habituelle du patient ne change pas. */
    private void appliquerLesTemporaires(CentreId centre, DonneesDOptimisation actuelles, List<DeplacementTemporairePropose> proposes) {
        List<DeplacementTemporaire> nouveaux = new ArrayList<>();
        for (DeplacementTemporairePropose t : proposes) {
            if (t.vers() == null || !t.vers().estComplet()) {
                throw new RegleDePlanificationViolee("DEPLACEMENT_TEMPORAIRE_CIBLE_INCOHERENTE", "La place de remplacement est incomplète");
            }
            nouveaux.add(DeplacementTemporaire.creer(identifiants.deplacementTemporaire(), centre, t.patient(), t.date(),
                    t.vers().salle(), t.vers().creneau(), t.vers().generateur(), t.motif(), actuelles.ressources()));
        }
        if (!nouveaux.isEmpty()) deplacements.enregistrer(nouveaux);
    }

    /** Prévient le médecin et le secrétariat quand l'application a déplacé des patients ou des séances. */
    private void signalerLesDeplacements(CentreId centre, Perimetre perimetre, ResultatDOptimisation resultat) {
        int patients = perimetre.placeLesPatients() ? resultat.deplacements().size() : 0;
        int temporaires = perimetre == Perimetre.MAINTENANCE ? resultat.temporaires().size() : 0;
        if (patients + temporaires > 0) notifications.seancesDeplacees(centre, perimetre, patients, temporaires);
    }
}
