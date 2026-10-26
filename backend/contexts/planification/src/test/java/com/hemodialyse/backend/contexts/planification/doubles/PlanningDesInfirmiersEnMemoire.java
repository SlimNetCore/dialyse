package com.hemodialyse.backend.contexts.planification.doubles;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.CalendrierDuCentre;
import com.hemodialyse.backend.contexts.planification.application.CreneauxDuCentre;
import com.hemodialyse.backend.contexts.planification.application.HorlogeDesCentres;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.NotificationsDesAbsencesDInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDuPlanning;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SemaineDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.SurEffectifDuRoulement;
import com.hemodialyse.backend.contexts.planification.domain.AbsenceInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.AbsencesRepository;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AbsenceId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.AffectationId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CreneauId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.RemplacementId;
import com.hemodialyse.backend.contexts.planification.domain.JourDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.PeriodeDAbsence;
import com.hemodialyse.backend.contexts.planification.domain.Remplacement;
import com.hemodialyse.backend.contexts.planification.domain.RemplacementsRepository;
import com.hemodialyse.backend.contexts.planification.domain.RoulementDeLInfirmier;
import com.hemodialyse.backend.contexts.planification.domain.RoulementsRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Doubles en mémoire du roulement, des absences, des remplacements et des ports de la slice P3. Chaque dépôt passe le même contrat que
 * son adaptateur JDBC ({@code PlanningDesInfirmiersContrat}).
 */
public final class PlanningDesInfirmiersEnMemoire {

    public static final CentreId CENTRE = new CentreId(UUID.fromString("99997814-0000-0000-0000-00000000000a"));

    public final InfirmiersEnMemoire monde = new InfirmiersEnMemoire();
    public final ParametrageEnMemoire parametrage = new ParametrageEnMemoire();

    private final Map<String, List<com.hemodialyse.backend.contexts.planification.domain.Affectation>> affectations = new LinkedHashMap<>();
    private final Map<AbsenceId, AbsenceInfirmier> absencesParId = new LinkedHashMap<>();
    private final Map<RemplacementId, Remplacement> remplacementsParId = new LinkedHashMap<>();

    public int transactions;
    public final Set<LocalDate> joursFermes = new HashSet<>();
    public LocalDate aujourdhui = LocalDate.of(2026, 11, 2);
    public final List<String> notifications = new ArrayList<>();
    public List<JourDeLaSemaine> sureffectif = List.of();
    public boolean sureffectifEnEchec;
    public SemaineDeLInfirmier.PlanningDeLaSemaine semaine;
    private int compteur = 1;

    public final RoulementsRepository roulements = new RoulementsRepository() {
        @Override
        public RoulementDeLInfirmier de(CentreId centre, InfirmierId infirmier) {
            return RoulementDeLInfirmier.reconstituer(centre, infirmier, affectations.getOrDefault(centre.valeur() + "|" + infirmier.valeur(), List.of()));
        }

        private List<com.hemodialyse.backend.contexts.planification.domain.Affectation> lignes(CentreId centre, InfirmierId infirmier) {
            return affectations.computeIfAbsent(centre.valeur() + "|" + infirmier.valeur(), k -> new ArrayList<>());
        }

        @Override
        public void ajouter(CentreId centre, InfirmierId infirmier, com.hemodialyse.backend.contexts.planification.domain.Affectation a) {
            lignes(centre, infirmier).add(a);
        }

        @Override
        public void remplacer(CentreId centre, InfirmierId infirmier, com.hemodialyse.backend.contexts.planification.domain.Affectation a) {
            lignes(centre, infirmier).replaceAll(x -> x.id().equals(a.id()) ? a : x);
        }

        @Override
        public void retirer(CentreId centre, InfirmierId infirmier, AffectationId affectation) {
            lignes(centre, infirmier).removeIf(x -> x.id().equals(affectation));
        }
    };

    public final AbsencesRepository absences = new AbsencesRepository() {
        @Override
        public void enregistrer(AbsenceInfirmier absence) {
            absencesParId.put(absence.id(), absence);
        }

        @Override
        public Optional<AbsenceInfirmier> parId(CentreId centre, AbsenceId id) {
            return Optional.ofNullable(absencesParId.get(id)).filter(a -> a.centre().equals(centre));
        }

        @Override
        public PageDeResultats<AbsenceInfirmier> page(CentreId centre, Pagination pagination) {
            return paginer(centre, null, pagination);
        }

        @Override
        public PageDeResultats<AbsenceInfirmier> pageDeLInfirmier(CentreId centre, InfirmierId infirmier, Pagination pagination) {
            return paginer(centre, infirmier, pagination);
        }

        private PageDeResultats<AbsenceInfirmier> paginer(CentreId centre, InfirmierId infirmier, Pagination pagination) {
            List<AbsenceInfirmier> toutes = absencesParId.values().stream()
                    .filter(a -> a.centre().equals(centre) && (infirmier == null || a.infirmier().equals(infirmier)))
                    .sorted(Comparator.comparing((AbsenceInfirmier a) -> a.periode().debut()).reversed()
                            .thenComparing(a -> a.id().valeur())).toList();
            int debut = (int) Math.min(pagination.decalage(), toutes.size());
            int fin = (int) Math.min(debut + (long) pagination.taille(), toutes.size());
            return new PageDeResultats<>(toutes.subList(debut, fin), toutes.size(), pagination.page(), pagination.taille());
        }

        @Override
        public boolean existeUneAbsenceSurLaPeriode(CentreId centre, InfirmierId infirmier, PeriodeDAbsence periode) {
            return absencesParId.values().stream().anyMatch(a -> a.centre().equals(centre) && a.infirmier().equals(infirmier)
                    && a.periode().chevauche(periode));
        }

        @Override
        public boolean absentLe(CentreId centre, InfirmierId infirmier, LocalDate jour) {
            return absencesParId.values().stream().anyMatch(a -> a.centre().equals(centre) && a.infirmier().equals(infirmier)
                    && a.couvre(jour));
        }

        @Override
        public void retirer(CentreId centre, AbsenceId id) {
            parId(centre, id).ifPresent(a -> absencesParId.remove(id));
        }
    };

    public final RemplacementsRepository remplacements = new RemplacementsRepository() {
        @Override
        public void enregistrer(Remplacement remplacement) {
            if (remplaceDeja(remplacement.centre(), remplacement.remplacant(), remplacement.date(), remplacement.creneau())) {
                throw new com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente();
            }
            remplacementsParId.put(remplacement.id(), remplacement);
        }

        @Override
        public Optional<Remplacement> parId(CentreId centre, RemplacementId id) {
            return Optional.ofNullable(remplacementsParId.get(id)).filter(r -> r.centre().equals(centre));
        }

        @Override
        public boolean remplaceDeja(CentreId centre, InfirmierId infirmier, LocalDate jour, CreneauId creneau) {
            return remplacementsParId.values().stream().anyMatch(r -> r.centre().equals(centre) && r.remplacant().equals(infirmier)
                    && r.date().equals(jour) && r.creneau().equals(creneau));
        }

        @Override
        public void annuler(CentreId centre, RemplacementId id) {
            parId(centre, id).ifPresent(r -> remplacementsParId.remove(id));
        }
    };

    public final com.hemodialyse.backend.contexts.planification.application.UniteDeTravail unite =
            new com.hemodialyse.backend.contexts.planification.application.UniteDeTravail() {
                @Override
                public <T> T executer(Supplier<T> action) {
                    transactions++;
                    return action.get();
                }
            };

    public final HorlogeDesCentres horloge = centre -> aujourdhui;
    public final CalendrierDuCentre calendrier = (centre, jour) -> !joursFermes.contains(jour);
    public final CreneauxDuCentre creneaux = parametrage.creneauxPort;

    public final NouveauxIdentifiantsDuPlanning identifiants = new NouveauxIdentifiantsDuPlanning() {
        @Override
        public AffectationId affectation() {
            return new AffectationId(suivant());
        }

        @Override
        public AbsenceId absence() {
            return new AbsenceId(suivant());
        }

        @Override
        public RemplacementId remplacement() {
            return new RemplacementId(suivant());
        }
    };

    public final NotificationsDesAbsencesDInfirmiers notificationsDuCentre = new NotificationsDesAbsencesDInfirmiers() {
        @Override
        public void absenceEnregistree(UUID centre, String infirmier, LocalDate debut, LocalDate fin, boolean prevenirAdministrateur) {
            notifications.add("ENREGISTREE " + infirmier + " " + debut + " " + fin + " admin=" + prevenirAdministrateur);
        }

        @Override
        public void absenceDeclareeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
            notifications.add("DECLAREE " + infirmier + " " + debut + " " + fin);
        }

        @Override
        public void absenceRetireeParLAdministration(UUID centre, String infirmier, LocalDate debut, LocalDate fin,
                                                     boolean prevenirAdministrateur) {
            notifications.add("RETIREE_ADMIN " + infirmier + " " + debut + " " + fin + " admin=" + prevenirAdministrateur);
        }

        @Override
        public void absenceRetireeParLInfirmier(UUID centre, String infirmier, LocalDate debut, LocalDate fin) {
            notifications.add("RETIREE_INFIRMIER " + infirmier + " " + debut + " " + fin);
        }
    };

    public final SurEffectifDuRoulement surEffectifDuRoulement = (centre, salle, creneau, jours, jour) -> {
        if (sureffectifEnEchec) {
            throw new IllegalStateException("calcul de présence indisponible");
        }
        return sureffectif.stream().map(Enum::name).toList();
    };

    /**
     * Le jour demandé à la dernière lecture de la semaine d'un infirmier.
     */
    public LocalDate dateDemandee;
    public final SemaineDeLInfirmier semaineDeLInfirmier = (centre, infirmier, date) -> {
        dateDemandee = date;
        return semaine;
    };

    private UUID suivant() {
        return new UUID(0x7814L, compteur++);
    }

    /**
     * Pose le monde minimal d'un centre : une salle, un créneau.
     */
    public Map<String, UUID> grille(CentreId centre, boolean salleDIsolement) {
        Map<String, UUID> ids = new HashMap<>();
        ids.put("salle", UUID.randomUUID());
        ids.put("creneau", UUID.randomUUID());
        parametrage.salles.ajouter(centre, new com.hemodialyse.backend.contexts.planification.domain.Identifiants.SalleId(ids.get("salle")),
                salleDIsolement);
        parametrage.creneaux.computeIfAbsent(centre, c -> new HashSet<>()).add(new CreneauId(ids.get("creneau")));
        return ids;
    }
}
