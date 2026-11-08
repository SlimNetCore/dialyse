package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.contrat.RaccourcisDesCentresEnMemoire;
import com.hemodialyse.backend.contexts.seance.contrat.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.ArticleDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.ArticlesDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.QuantiteConsommee;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Banc des tests d'application : tous les ports sortants en mémoire (doubles fidèles, pas de mock interactionnel). Un patient est
 * enregistré avec {@link #patient}; la session est celle d'un utilisateur de rôle donné.
 */
final class Banc {

    static final CentreId CENTRE_A = new CentreId(new UUID(0, 0xA));
    static final CentreId CENTRE_B = new CentreId(new UUID(0, 0xB));
    static final Instant MAINTENANT = Instant.parse("2026-06-15T10:00:00Z");
    static final LocalDate JOUR = LocalDate.of(2026, 6, 15);
    static final SituationDuPlanning ATTENDU = new SituationDuPlanning(false, true, false, null, null, null);

    final SeancesEnMemoire seances = new SeancesEnMemoire();
    final Session session = new Session();
    final Patients patients = new Patients();
    final Map<String, SituationDuPlanning> situations = new HashMap<>();
    final Map<String, CauseDeNonFacturation> causes = new HashMap<>();
    final List<String> diffusionsRecues = new ArrayList<>();
    final Map<UUID, GenerateursDesPatients.Generateur> equipements = new HashMap<>();
    /** La lecture de la place échoue : prouve qu'un échec pendant la validation du scan défait aussi la création. */
    boolean laPlaceEchoue;
    private int compteur = 100;
    private Clock horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);

    void horlogeA(String instant) {
        horloge = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    HorlogeDuCentre horloge() {
        return new HorlogeDuCentre(horloge, ZoneId.of("Africa/Algiers"));
    }

    PatientId patient(CentreId centre, int n, String code, String numero) {
        PatientId id = new PatientId(new UUID(7, n));
        patients.fiches.put(cle(centre, id), new FicheDuPatient(id, code, "Nom" + n, "Prenom" + n, "M", LocalDate.of(1970, 1, 1), numero,
                null, null, null, null, null, null, null, null, null));
        patients.references.computeIfAbsent(centre, c -> new ArrayList<>())
                .add(new PatientsScannables.References(id, code, numero, null));
        situations.put(cle(centre, id), ATTENDU);
        return id;
    }

    static String cle(CentreId centre, PatientId patient) {
        return centre.valeur() + "/" + patient.valeur();
    }

    GenerateurDIdentifiants identifiants() {
        return () -> new UUID(9, compteur++);
    }

    UniteDeTravail unite() {
        return new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> action) {
                var avant = seances.instantane();
                try {
                    return action.get();
                } catch (RuntimeException e) {
                    seances.restaurer(avant);
                    throw e;
                }
            }
        };
    }

    PlanningDesPatients planning() {
        return (centre, patient, jour) -> situations.get(cle(centre, patient));
    }

    EligibiliteALaFacturation eligibilite() {
        return (centre, patient, jour) -> Optional.ofNullable(causes.get(cle(centre, patient)));
    }

    GenerateursDesPatients generateurs() {
        return (centre, equipement) -> Optional.ofNullable(equipements.get(equipement));
    }

    DiffusionsDeSeance diffusions() {
        return new DiffusionsDeSeance() {
            @Override
            public void seanceCreee(CentreId centre, Seance seance, FicheDuPatient patient) {
                diffusionsRecues.add("SEANCE_CREATED:" + patient.nom());
            }

            @Override
            public void seanceValidee(CentreId centre, Seance seance, FicheDuPatient patient) {
                diffusionsRecues.add("SEANCE_VALIDATED:" + patient.nom());
            }

            @Override
            public void saisieDeLInfirmier(CentreId centre, String type, Seance seance, FicheDuPatient patient, String auteur) {
                diffusionsRecues.add(type + ":" + auteur);
            }

            @Override
            public void seanceDeverrouillee(CentreId centre, Seance seance, FicheDuPatient patient) {
                diffusionsRecues.add("SEANCE_DEVERROUILLEE:" + patient.nom());
            }

            @Override
            public void consommablesModifies(CentreId centre, Seance seance) {
                diffusionsRecues.add("SEANCE_CONSOMMABLE_CHANGED:" + seance.id().valeur());
            }

            @Override
            public void seanceSupprimee(CentreId centre, SuppressionDeSeance suppression) {
                diffusionsRecues.add("SEANCE_SUPPRIMEE:" + suppression.seance().valeur() + ":" + suppression.jour());
            }
        };
    }

    // ---- SE6 : changement de jour et suppression

    /** Appels dans l'ordre : « consommations », « saisies », « suppression » (ligne), « journal » : prouve l'ordre du legacy. */
    final List<String> etapesDeLaSuppression = new ArrayList<>();
    final List<SuppressionDeSeance> journal = new ArrayList<>();
    /** Le Stock refuse l'annulation (séance facturée, pièce rapprochée) avec ce code. */
    String refusDeLAnnulation;
    /** Le journal échoue : prouve que la suppression est tout ou rien. */
    boolean leJournalEchoue;

    AnnulationDesConsommations annulationDesConsommations() {
        return (centre, seance, auteur) -> {
            if (refusDeLAnnulation != null) throw new ConsommationRefusee(refusDeLAnnulation, "refus du Stock");
            etapesDeLaSuppression.add("consommations:" + auteur);
        };
    }

    SaisiesDeLaSeance saisiesDeLaSeance() {
        return (centre, seance) -> etapesDeLaSuppression.add("saisies");
    }

    JournalDesSuppressions journalDesSuppressions() {
        return suppression -> {
            if (leJournalEchoue) throw new IllegalStateException("journal indisponible");
            etapesDeLaSuppression.add("journal");
            journal.add(suppression);
        };
    }

    ChangerLeJourDUneSeance changerLeJour() {
        return new ChangerLeJourDUneSeance(session, seances, unite(), horloge());
    }

    SupprimerUneSeance supprimer() {
        Seances tracee = new Seances() {
            @Override
            public void ajouter(Seance seance) {
                seances.ajouter(seance);
            }

            @Override
            public void verrouillerLeJour(CentreId centre, PatientId patient, LocalDate jour) {
                seances.verrouillerLeJour(centre, patient, jour);
            }

            @Override
            public List<Seance> duJour(CentreId centre, PatientId patient, LocalDate jour) {
                return seances.duJour(centre, patient, jour);
            }

            @Override
            public List<Seance> dernieres(CentreId centre, PatientId patient, LocalDate avant,
                                          com.hemodialyse.backend.contexts.seance.domain.NombreDeSeancesRecentes nombre) {
                return seances.dernieres(centre, patient, avant, nombre);
            }

            @Override
            public Optional<Seance> trouver(CentreId centre, SeanceId id) {
                return seances.trouver(centre, id);
            }

            @Override
            public Optional<Seance> trouverPourModifier(CentreId centre, SeanceId id) {
                return seances.trouverPourModifier(centre, id);
            }

            @Override
            public void enregistrer(Seance seance) {
                seances.enregistrer(seance);
            }

            @Override
            public void enregistrerLeForfaitImpose(Seance seance) {
                seances.enregistrerLeForfaitImpose(seance);
            }

            @Override
            public void enregistrerLeJour(Seance seance) {
                seances.enregistrerLeJour(seance);
            }

            @Override
            public void supprimer(Seance seance) {
                etapesDeLaSuppression.add("suppression");
                seances.supprimer(seance);
            }
        };
        return new SupprimerUneSeance(session, tracee, annulationDesConsommations(), saisiesDeLaSeance(), journalDesSuppressions(), unite(),
                identifiants(), horloge(), diffusions());
    }

    /** SE3 : quantités déclarées au Stock par « séance/article » (le double cumule, fixe et retire comme le Stock) et auteurs transmis. */
    final Map<String, BigDecimal> declarees = new LinkedHashMap<>();
    final List<String> auteursTransmis = new ArrayList<>();

    ConsommablesDeLaSeance consommables() {
        return new ConsommablesDeLaSeance() {
            @Override
            public void ajouter(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur) {
                verifier(article, true);
                declarees.merge(cle(seance, article), quantite.valeur(), BigDecimal::add);
                auteursTransmis.add(auteur);
            }

            @Override
            public void remplacer(CentreId centre, Seance seance, ArticleId article, QuantiteConsommee quantite, String auteur) {
                verifier(article, quantite.valeur().compareTo(declarees.getOrDefault(cle(seance, article), BigDecimal.ZERO)) > 0);
                declarees.put(cle(seance, article), quantite.valeur());
                auteursTransmis.add(auteur);
            }

            @Override
            public void retirer(CentreId centre, Seance seance, ArticleId article, String auteur) {
                verifier(article, false);
                declarees.remove(cle(seance, article));
                auteursTransmis.add(auteur);
            }

            /** Comme le Stock : article inconnu, ou inactif quand la quantité augmente : refus de saisie (400). */
            private void verifier(ArticleId article, boolean augmente) {
                if (refusDuStock != null) throw new ConsommationRefusee(refusDuStock, "refus du Stock");
                var fiche = articles.get(article.valeur());
                if (fiche == null) throw new SaisieInvalide("Article introuvable: " + article.valeur());
                if (augmente && !fiche.actif()) throw new SaisieInvalide("Article inactif: " + fiche.code());
            }
        };
    }

    private static String cle(Seance seance, ArticleId article) {
        return seance.id().valeur() + "/" + article.valeur();
    }

    AjouterUnConsommable ajouterUnConsommable() {
        return new AjouterUnConsommable(session, seances, patients, consommables(), unite(), diffusions());
    }

    ModifierLaQuantiteDUnConsommable modifierUnConsommable() {
        return new ModifierLaQuantiteDUnConsommable(session, seances, patients, consommables(), unite(), diffusions());
    }

    RetirerUnConsommable retirerUnConsommable() {
        return new RetirerUnConsommable(session, seances, patients, consommables(), unite(), diffusions());
    }

    /** SE3 : raccourcis par centre, en mémoire (même contrat que la base). */
    final RaccourcisDesCentresEnMemoire raccourcis = new RaccourcisDesCentresEnMemoire();

    ArticlesDuCentre catalogue() {
        return (centre, article) -> Optional.ofNullable(articles.get(article.valeur()))
                .map(a -> new ArticleDuCentre(new ArticleId(a.id()), a.code(), a.actif()));
    }

    ConsulterLesRaccourcis consulterLesRaccourcis() {
        return new ConsulterLesRaccourcis(session, raccourcis);
    }

    RemplacerLesRaccourcis remplacerLesRaccourcis() {
        return new RemplacerLesRaccourcis(session, catalogue(), raccourcis, unite());
    }

    /** Places des patients (le jour n'importe pas pour le double). */
    final Map<String, PlaceDeSeance> places = new HashMap<>();
    /** Articles du catalogue, par identifiant. */
    final Map<UUID, ConsommationsALaValidation.Article> articles = new HashMap<>();
    /** Consommables enregistrés à la validation : « séance/article/quantité/auteur ». */
    final List<String> consommationsEnregistrees = new ArrayList<>();
    String refusDuStock;

    PlaceDuPatient placeDuPatient() {
        return (centre, patient, jour) -> {
            if (laPlaceEchoue) throw new IllegalStateException("place illisible");
            return Optional.ofNullable(places.get(cle(centre, patient)));
        };
    }

    ConsommationsALaValidation consommationsALaValidation() {
        return new ConsommationsALaValidation() {
            @Override
            public Optional<Article> article(CentreId centre, UUID article) {
                return Optional.ofNullable(articles.get(article));
            }

            @Override
            public void enregistrer(CentreId centre, Seance seance, List<Ligne> lignes, String auteur) {
                if (refusDuStock != null) throw new ConsommationRefusee(refusDuStock, "refus du Stock");
                for (Ligne l : lignes) {
                    consommationsEnregistrees.add(seance.id().valeur() + "/" + l.article() + "/" + l.quantite() + "/" + auteur);
                }
            }
        };
    }

    ValiderUneSeance valider() {
        return new ValiderUneSeance(session, patients, seances, placeDuPatient(), consommationsALaValidation(), unite(), horloge(),
                diffusions());
    }

    SignerUneSeance signer() {
        return new SignerUneSeance(session, seances, unite(), horloge());
    }

    DeverrouillerUneSeance deverrouiller() {
        return new DeverrouillerUneSeance(session, patients, seances, unite(), horloge(), diffusions());
    }

    ResoudreLePatientScanne resolution() {
        return new ResoudreLePatientScanne(patients);
    }

    CreerUneSeance creer() {
        return new CreerUneSeance(session, patients, generateurs(), eligibilite(), seances, unite(), identifiants(), horloge(),
                diffusions());
    }

    ScannerUnPatient scanner() {
        return new ScannerUnPatient(session, resolution(), patients, generateurs(), planning(), eligibilite(), seances, placeDuPatient(),
                unite(), identifiants(), horloge(), diffusions());
    }

    /** Session de test : centre fixe, rôle choisi. */
    static final class Session implements SessionCourante {
        CentreId centre = CENTRE_A;
        String utilisateur = "u-infirmier";
        boolean administrateur;
        boolean valide = true;

        @Override
        public CentreId centre(UUID demande) {
            if (demande != null && !demande.equals(centre.valeur())) throw new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session");
            return centre;
        }

        @Override
        public String utilisateur() {
            return utilisateur;
        }

        @Override
        public boolean estAdministrateur() {
            return administrateur;
        }

        @Override
        public boolean valideAuScan() {
            return valide;
        }

        void secretaire() {
            utilisateur = "u-secretaire";
            valide = false;
            administrateur = false;
        }

        void administrateur() {
            utilisateur = "u-admin";
            valide = true;
            administrateur = true;
        }
    }

    /** Fiches des patients, par centre : un patient d'un autre centre est introuvable. */
    static final class Patients implements PatientsDeLaSeance, PatientsScannables {
        final Map<String, FicheDuPatient> fiches = new HashMap<>();
        final Map<CentreId, List<References>> references = new HashMap<>();

        @Override
        public Optional<FicheDuPatient> fiche(CentreId centre, PatientId patient) {
            return Optional.ofNullable(fiches.get(cle(centre, patient)));
        }

        @Override
        public Optional<PatientId> parIdentifiant(CentreId centre, UUID identifiant) {
            return fiche(centre, new PatientId(identifiant)).map(FicheDuPatient::id);
        }

        @Override
        public Optional<PatientId> parCode(CentreId centre, String code) {
            return tous(centre).stream().filter(r -> r != null && code.equalsIgnoreCase(r.code())).map(References::patient).findFirst();
        }

        @Override
        public Optional<PatientId> parNumeroDAssurance(CentreId centre, String numero) {
            return tous(centre).stream().filter(r -> r != null && numero.equalsIgnoreCase(r.numeroDAssurance())).map(References::patient).findFirst();
        }

        @Override
        public List<References> tous(CentreId centre) {
            return references.getOrDefault(centre, List.of());
        }
    }

    Seance ligne(CentreId centre, PatientId patient, int n, StatutDeSeance statut) {
        return Seance.restituer(new SeanceId(new UUID(5, n)), centre, patient, JOUR, statut, MAINTENANT.minusSeconds(60L * (100 - n)),
                null, null);
    }
}
