package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.contrat.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PlaceDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SituationDuPlanning;
import com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
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
    final List<String> validations = new ArrayList<>();
    final Map<UUID, GenerateursDesPatients.Generateur> equipements = new HashMap<>();
    boolean lAdapterDeValidationEchoue;
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

    ValidationDeSeance validation() {
        return (centre, seance, auteur) -> {
            if (lAdapterDeValidationEchoue) throw new IllegalStateException("validation refusée");
            validations.add(seance.valeur() + "/" + auteur);
            Seance avant = seances.trouver(centre, seance).orElseThrow();
            Seance validee = Seance.restituer(avant.id(), avant.centre(), avant.patient(), avant.jour(), StatutDeSeance.VALIDEE,
                    avant.creeLe(), avant.derogation().orElse(null), new Seance.Jalons(MAINTENANT, MAINTENANT, null, null));
            seances.semer(validee);
            return validee;
        };
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
        };
    }

    /** Places des patients (le jour n'importe pas pour le double). */
    final Map<String, PlaceDeSeance> places = new HashMap<>();
    /** Articles du catalogue, par identifiant. */
    final Map<UUID, ConsommationsALaValidation.Article> articles = new HashMap<>();
    /** Consommables enregistrés à la validation : « séance/article/quantité/auteur ». */
    final List<String> consommationsEnregistrees = new ArrayList<>();
    String refusDuStock;

    PlaceDuPatient placeDuPatient() {
        return (centre, patient, jour) -> Optional.ofNullable(places.get(cle(centre, patient)));
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
        return new ScannerUnPatient(session, resolution(), patients, generateurs(), planning(), eligibilite(), seances, validation(),
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
