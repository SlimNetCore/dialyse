package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.Empreinte;
import com.hemodialyse.backend.contexts.planification.domain.ExecutionDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.ExecutionId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.GenerateurId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.ParametresDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.Poste;
import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.CauseNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementDePatient;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.DeplacementTemporairePropose;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.Indicateurs;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.PatientNonPlace;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.SeanceSansSolution;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationNonPourvue;
import com.hemodialyse.backend.contexts.planification.domain.ResultatDOptimisation.VacationPlanifiee;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Modèle de LECTURE d'une exécution d'optimisation, aux noms de champs du JSON historique (le contrat HTTP et le contenu des colonnes
 * JSON de {@code planification_optimisation} ne changent pas) : jamais l'agrégat vers l'interface, jamais d'identifiant typé dans le JSON.
 * Sert aussi à écrire et relire ces colonnes ; {@link #versLAgregat} reconstitue l'agrégat sans rejouer ses règles de transition.
 */
public final class OptimisationLue {

    private OptimisationLue() {
    }

    public static ExecutionLue depuis(ExecutionDOptimisation e) {
        return new ExecutionLue(e.id().valeur(), e.centre().valeur(), e.statut(), e.parametres(), e.creeLe(), e.termineLe(),
                e.lancePar(), e.phase(), e.score(), e.empreinte() == null ? null : e.empreinte().valeur(), e.resume(),
                e.resultat() == null ? null : depuis(e.resultat()), e.erreur(), e.appliqueeLe());
    }

    private static <A, B> List<B> liste(List<A> source, Function<A, B> f) {
        return source.stream().map(f).toList();
    }

    public static ResultatLu depuis(ResultatDOptimisation r) {
        return new ResultatLu(liste(r.salles(), s -> new SalleLue(s.id().valeur(), s.nom())),
                liste(r.creneaux(), c -> new CreneauLu(c.id().valeur(), c.libelle(), c.ordre())),
                liste(r.deplacements(), d -> new DeplacementLu(d.patient().valeur(), d.nom(), poste(d.de()), poste(d.vers()), d.jours())),
                liste(r.nonPlaces(), n -> new NonPlaceLu(n.patient().valeur(), n.nom(), n.cause())),
                liste(r.vacations(), v -> new VacationLue(v.date(), v.jour(), v.salle().valeur(), v.creneau().valeur(),
                        v.infirmier().valeur(), v.nom(), v.existante())),
                liste(r.manques(), m -> new ManqueLu(m.date(), m.jour(), m.salle().valeur(), m.creneau().valeur(), m.manque())),
                r.avant(), r.apres(),
                liste(r.temporaires(), t -> new TemporaireLu(t.patient().valeur(), t.nom(), t.date(), t.jour(), poste(t.de()),
                        poste(t.vers()), t.motif())),
                liste(r.seancesSansSolution(), s -> new SansSolutionLue(s.patient().valeur(), s.nom(), s.date(), s.jour(), poste(s.de()),
                        s.motif())));
    }

    private static PosteLu poste(Poste p) {
        return p == null ? null : new PosteLu(p.salle() == null ? null : p.salle().valeur(),
                p.creneau() == null ? null : p.creneau().valeur(), p.generateur() == null ? null : p.generateur().valeur(),
                p.generateurCode());
    }

    /**
     * Reconstitue l'exécution lue de la persistance (aucune règle de transition rejouée).
     */
    public static ExecutionDOptimisation versLAgregat(ExecutionLue l) {
        return new ExecutionDOptimisation(new ExecutionId(l.id()), new CentreId(l.centerId()), l.statut(), l.parametres(), l.creeLe(),
                l.termineLe(), l.lancePar(), l.phase(), l.score(), l.empreinte() == null ? null : new Empreinte(l.empreinte()),
                l.resume(), l.resultat() == null ? null : versLeResultat(l.resultat()), l.erreur(), l.appliqueLe());
    }

    public static ResultatDOptimisation versLeResultat(ResultatLu r) {
        return new ResultatDOptimisation(
                liste(nonNul(r.salles()), s -> new RessourcesDuCentre.Salle(new SalleId(s.id()), s.nom(), false)),
                liste(nonNul(r.creneaux()), c -> new RessourcesDuCentre.Creneau(new CreneauId(c.id()), c.libelle(), c.ordre())),
                liste(nonNul(r.deplacements()), d -> new DeplacementDePatient(new PatientId(d.patientId()), d.nom(), poste(d.de()),
                        poste(d.vers()), d.jours())),
                liste(nonNul(r.nonPlaces()), n -> new PatientNonPlace(new PatientId(n.patientId()), n.nom(), n.cause())),
                liste(nonNul(r.vacations()), v -> new VacationPlanifiee(v.date(), v.jour(), new SalleId(v.salleId()),
                        new CreneauId(v.creneauId()), new InfirmierId(v.infirmierId()), v.nom(), v.existante())),
                liste(nonNul(r.manques()), m -> new VacationNonPourvue(m.date(), m.jour(), new SalleId(m.salleId()),
                        new CreneauId(m.creneauId()), m.manque())),
                r.avant(), r.apres(),
                liste(nonNul(r.temporaires()), t -> new DeplacementTemporairePropose(new PatientId(t.patientId()), t.nom(), t.date(),
                        t.jour(), poste(t.de()), poste(t.vers()), t.motif())),
                liste(nonNul(r.seancesSansSolution()), s -> new SeanceSansSolution(new PatientId(s.patientId()), s.nom(), s.date(),
                        s.jour(), poste(s.de()), s.motif())));
    }

    private static <T> List<T> nonNul(List<T> liste) {
        return liste == null ? List.of() : liste;
    }

    private static Poste poste(PosteLu p) {
        return p == null ? null : new Poste(p.salleId() == null ? null : new SalleId(p.salleId()),
                p.creneauId() == null ? null : new CreneauId(p.creneauId()),
                p.generateurId() == null ? null : new GenerateurId(p.generateurId()), p.generateurCode());
    }

    public record ExecutionLue(UUID id, UUID centerId, ExecutionDOptimisation.Statut statut,
                               ParametresDOptimisation parametres,
                               Instant creeLe, Instant termineLe, String lancePar, String phase, String score,
                               String empreinte,
                               ResultatDOptimisation.Resume resume, ResultatLu resultat, String erreur,
                               Instant appliqueLe) {
    }

    public record SalleLue(UUID id, String nom) {
    }

    public record CreneauLu(UUID id, String libelle, int ordre) {
    }

    // ───────────────────────────── agrégat -> lecture ─────────────────────────────

    public record PosteLu(UUID salleId, UUID creneauId, UUID generateurId, String generateurCode) {
    }

    public record DeplacementLu(UUID patientId, String nom, PosteLu de, PosteLu vers, List<JourDeLaSemaine> jours) {
    }

    public record NonPlaceLu(UUID patientId, String nom, CauseNonPlace cause) {
    }

    public record VacationLue(LocalDate date, JourDeLaSemaine jour, UUID salleId, UUID creneauId, UUID infirmierId,
                              String nom,
                              boolean existante) {
    }

    // ───────────────────────────── lecture -> agrégat ─────────────────────────────

    public record ManqueLu(LocalDate date, JourDeLaSemaine jour, UUID salleId, UUID creneauId, int manque) {
    }

    public record TemporaireLu(UUID patientId, String nom, LocalDate date, JourDeLaSemaine jour, PosteLu de,
                               PosteLu vers,
                               String motif) {
    }

    public record SansSolutionLue(UUID patientId, String nom, LocalDate date, JourDeLaSemaine jour, PosteLu de,
                                  String motif) {
    }

    public record ResultatLu(List<SalleLue> salles, List<CreneauLu> creneaux, List<DeplacementLu> deplacements,
                             List<NonPlaceLu> nonPlaces, List<VacationLue> vacations, List<ManqueLu> manques,
                             Indicateurs avant,
                             Indicateurs apres, List<TemporaireLu> temporaires,
                             List<SansSolutionLue> seancesSansSolution) {
    }
}
