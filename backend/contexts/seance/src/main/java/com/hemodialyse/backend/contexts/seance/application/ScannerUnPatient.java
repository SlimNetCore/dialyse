package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.CodeScanne;
import com.hemodialyse.backend.contexts.seance.domain.DerogationHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.MotifDeRefus;
import com.hemodialyse.backend.contexts.seance.domain.MotifHorsPlanning;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.ProgrammationDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceDuJourAmbigue;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.Signataire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Scan d'un patient (POST /api/v1/seances/scan). Administrateur et infirmier : la séance du jour est validée directement (créée puis
 * validée s'il n'y en a pas, validée si elle était « créée », rendue telle quelle si déjà validée) ; la validation est celle de la Séance (SE2,
 * intention de {@link Seance}), dans la même unité de travail que la création. Secrétaire (qui ne valide pas) : la séance du jour est
 * créée ou rendue, à valider par l'infirmier. Le jour est celui du centre (KB-SEA-01). Une création hors planning exige une dérogation
 * (motif, précision), que seul l'administrateur peut employer pour franchir un jour de fermeture ; l'ordre des contrôles est planning,
 * prise en charge, attestation. Plusieurs séances le même jour : 409 (KB-SEA-02). Le drapeau {@code creee} de la secrétaire reste faux et
 * les diffusions reprennent à chaque scan tant que la séance est « créée » (KB-SEA-05, reproduits).
 */
public class ScannerUnPatient {

    private final SessionCourante session;
    private final ResoudreLePatientScanne resolution;
    private final PatientsDeLaSeance patients;
    private final GenerateursDesPatients generateurs;
    private final PlanningDesPatients planning;
    private final EligibiliteALaFacturation eligibilite;
    private final Seances seances;
    private final PlaceDuPatient places;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public ScannerUnPatient(SessionCourante session, ResoudreLePatientScanne resolution, PatientsDeLaSeance patients,
                            GenerateursDesPatients generateurs, PlanningDesPatients planning, EligibiliteALaFacturation eligibilite,
                            Seances seances, PlaceDuPatient places, UniteDeTravail uniteDeTravail,
                            GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.resolution = resolution;
        this.patients = patients;
        this.generateurs = generateurs;
        this.planning = planning;
        this.eligibilite = eligibilite;
        this.seances = seances;
        this.places = places;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    /**
     * @param motifHorsPlanning       motif de dérogation en texte (casse et espaces ignorés), nul ou vide : sans dérogation
     * @param precisionHorsPlanning   précision du motif (255 caractères au plus)
     */
    public ResultatDuScan executer(UUID centreDemande, String qr, String motifHorsPlanning, String precisionHorsPlanning) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        String auteur = session.utilisateur();
        boolean valide = session.valideAuScan();
        // La dérogation est lue avant le QR, comme le legacy : un motif inconnu est refusé avant toute recherche (422).
        DerogationHorsPlanning derogation = valide ? lireLaDerogation(motifHorsPlanning, precisionHorsPlanning) : null;
        CodeScanne code = CodeScanne.lire(qr);

        ResultatDuScan resultat = uniteDeTravail.executer(() -> {
            PatientId patient = resolution.executer(centre, code);
            LocalDate jour = horloge.aujourdhui(centre);
            seances.verrouillerLeJour(centre, patient, jour);
            var duJour = seances.duJour(centre, patient, jour);
            if (duJour.size() > 1) throw new SeanceDuJourAmbigue();
            Optional<Seance> existante = duJour.stream().findFirst();
            FicheDuPatient fiche = patients.fiche(centre, patient)
                    .orElseThrow(PatientIntrouvable::new);
            fiche = Generateurs.preciser(generateurs, centre, fiche);
            return valide
                    ? scannerPourValider(centre, patient, jour, existante, derogation, auteur, fiche)
                    : scannerPourCreer(centre, patient, jour, existante, fiche);
        });
        diffuser(centre, resultat, auteur);
        return resultat;
    }

    private ResultatDuScan scannerPourValider(CentreId centre, PatientId patient, LocalDate jour, Optional<Seance> existante,
                                              DerogationHorsPlanning derogation, String auteur, FicheDuPatient fiche) {
        if (existante.isEmpty()) {
            Seance creee = creer(centre, patient, jour, derogation);
            return new ResultatDuScan(valider(centre, creee, auteur), fiche, true, true, false);
        }
        Seance seance = existante.get();
        return switch (seance.statut()) {
            case CREE -> new ResultatDuScan(valider(centre, seance, auteur), fiche, false, true, false);
            case VALIDEE, SIGNEE, FACTUREE -> new ResultatDuScan(seance, fiche, false, false, true);
            case ABSENT -> throw new SeanceRefusee(MotifDeRefus.SEANCE_DU_JOUR_ABSENTE,
                    "La séance du jour de ce patient est marquée absente : elle ne peut pas être validée");
        };
    }

    /**
     * Validation par le scan (SE2) : la séance passe à validée au nom du compte de la session (KB-SEA-09), sa place du jour est mémorisée,
     * sans consommable ni verrou des jours passés (le scan vaut pour le jour du centre). Même unité de travail que la création.
     */
    private Seance valider(CentreId centre, Seance seance, String auteur) {
        seance.validerParLInfirmier(horloge.maintenant(), Signataire.lire(auteur));
        if (seance.traces().place() == null) {
            places.placeLe(centre, seance.patient(), seance.jour()).ifPresent(seance::memoriserPlace);
        }
        seances.enregistrer(seance);
        return seance;
    }

    private ResultatDuScan scannerPourCreer(CentreId centre, PatientId patient, LocalDate jour, Optional<Seance> existante,
                                            FicheDuPatient fiche) {
        Seance seance = existante.orElseGet(() -> creer(centre, patient, jour, null));
        return new ResultatDuScan(seance, fiche, false, false, !seance.attendLaValidation());
    }

    /** Planning, puis prise en charge et attestation, puis création : l'ordre des refus est celui du legacy. */
    private Seance creer(CentreId centre, PatientId patient, LocalDate jour, DerogationHorsPlanning derogation) {
        Optional<DerogationHorsPlanning> retenue =
                ProgrammationDuPatient.exiger(planning.situation(centre, patient, jour), jour, derogation);
        CauseDeNonFacturation.exigerFacturable(eligibilite.cause(centre, patient, jour), jour);
        SeanceId id = new SeanceId(identifiants.suivant());
        Seance seance = retenue.isPresent()
                ? Seance.creerHorsPlanning(id, centre, patient, jour, horloge.maintenant(), retenue.get())
                : Seance.creer(id, centre, patient, jour, horloge.maintenant());
        seances.ajouter(seance);
        return seance;
    }

    private DerogationHorsPlanning lireLaDerogation(String motif, String precision) {
        if (motif == null || motif.isBlank()) return null;
        return new DerogationHorsPlanning(MotifHorsPlanning.lire(motif), precision, session.estAdministrateur());
    }

    private void diffuser(CentreId centre, ResultatDuScan r, String auteur) {
        if (!r.dejaValidee()) diffusions.seanceCreee(centre, r.seance(), r.patient());
        if (r.validee()) {
            diffusions.seanceValidee(centre, r.seance(), r.patient());
            diffusions.saisieDeLInfirmier(centre, "SEANCE_VALIDEE", r.seance(), r.patient(), auteur);
        } else if (!r.dejaValidee()) {
            diffusions.saisieDeLInfirmier(centre, "SEANCE_CREEE", r.seance(), r.patient(), auteur);
        }
    }

    /**
     * @param creee        la séance vient d'être créée (jamais vrai pour la secrétaire : KB-SEA-05 reproduit)
     * @param validee      la séance vient d'être validée par ce scan
     * @param dejaValidee  la séance n'attendait plus de validation
     */
    public record ResultatDuScan(Seance seance, FicheDuPatient patient, boolean creee, boolean validee, boolean dejaValidee) {
    }
}
