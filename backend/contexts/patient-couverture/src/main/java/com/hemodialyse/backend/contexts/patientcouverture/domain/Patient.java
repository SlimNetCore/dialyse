package com.hemodialyse.backend.contexts.patientcouverture.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Patient (racine) : fiche administrative d'un patient d'un centre, son état dans le centre et ses mouvements.
 * <p>
 * Invariants : (1) un état de sortie ou de séjour limité exige une date d'évènement, jamais antérieure à l'admission ;
 * (2) la naissance n'est ni future ni postérieure à l'admission ; (3) l'état appartient à la liste fermée
 * {@link EtatDuPatient} ; (4) tout changement d'état est tracé par un mouvement daté ; (5) l'attestation de droits est exigée
 * sauf pour un vacancier. Le temps (« aujourd'hui » du centre, instant) est passé en paramètre.
 */
public final class Patient {

    private final PatientId id;
    private final String code;
    private final CentreId centre;
    private final boolean vacancier;
    private final Instant creeLe;
    private Fiche fiche;
    private final List<MouvementDuPatient> mouvements = new ArrayList<>();

    private Patient(PatientId id, String code, CentreId centre, boolean vacancier, Instant creeLe, Fiche fiche) {
        this.id = id;
        this.code = code;
        this.centre = centre;
        this.vacancier = vacancier;
        this.creeLe = creeLe;
        this.fiche = fiche;
    }

    /** Admet un nouveau patient : mouvement {@code ADMISSION} daté de l'admission. */
    public static Patient admettre(PatientId id, String code, CentreId centre, Fiche saisie, LocalDate aujourdhui,
                                   Instant maintenant, Supplier<UUID> identifiants) {
        EtatDuPatient etat = saisie.etat() == null ? EtatDuPatient.PERMANENT : saisie.etat();
        Fiche fiche = saisie.avecEtat(etat);
        verifier(fiche, aujourdhui);
        fiche = aligner(fiche);
        Patient patient = new Patient(id, code, centre, etat.vacancier(), maintenant, fiche);
        patient.tracer(identifiants, TypeDeMouvement.ADMISSION, fiche.dateAdmission(), null, maintenant);
        return patient;
    }

    /** Relit un patient enregistré ; un état nul d'une ligne héritée vaut {@code PERMANENT} (décision du 02/11, KB-PAT-09). */
    public static Patient reconstituer(PatientId id, String code, CentreId centre, boolean vacancier, Instant creeLe,
                                       Fiche enregistree) {
        EtatDuPatient etat = enregistree.etat() == null ? EtatDuPatient.PERMANENT : enregistree.etat();
        return new Patient(id, code, centre, vacancier, creeLe, enregistree.avecEtat(etat));
    }

    /**
     * Enregistre une nouvelle saisie de la fiche. Un mouvement est tracé si l'état change, ou si, hors états de sortie, la
     * date d'évènement change ; corriger la seule date d'une sortie déjà enregistrée ne crée pas un second mouvement
     * (décision du 02/11, KB-PAT-05).
     */
    public void modifier(Fiche saisie, LocalDate aujourdhui, Instant maintenant, Supplier<UUID> identifiants) {
        EtatDuPatient avant = fiche.etat();
        LocalDate dateAvant = fiche.dateEvenement();
        EtatDuPatient apres = saisie.etat() == null ? avant : saisie.etat();
        Fiche nouvelle = saisie.avecEtat(apres);
        verifier(nouvelle, aujourdhui);
        nouvelle = aligner(nouvelle);
        this.fiche = nouvelle;
        boolean etatChange = avant != apres;
        boolean dateChange = !java.util.Objects.equals(dateAvant, nouvelle.dateEvenement());
        if (etatChange || (dateChange && !apres.sortie())) {
            LocalDate dateEffet = nouvelle.dateEvenement() != null ? nouvelle.dateEvenement() : aujourdhui;
            tracer(identifiants, apres.mouvement(), dateEffet, avant, maintenant);
        }
    }

    /**
     * Assuré lui-même : l'assuré de la fiche est le patient (son numéro, son état civil). Assuré tiers : le numéro de
     * l'assuré est obligatoire.
     */
    private static Fiche aligner(Fiche fiche) {
        Couverture c = fiche.couverture();
        if (c.assureLuiMeme()) {
            Identite i = fiche.identite();
            Coordonnees k = fiche.coordonnees();
            return fiche.avecCouverture(new Couverture(c.numeroAssurance(), c.qualiteAssure(), c.centrePayeurId(),
                    c.numeroAssurance().valeur(),
                    new Couverture.AssureSurLaFiche(i.sexe(), i.nom(), i.prenom(), i.dateNaissance().toString(),
                            k.telPersonnel(), k.adresse(), i.groupeSanguin(), k.telMobile(), k.telBureau()),
                    c.assureHistoriqueJson(), c.piecesJointesJson()));
        }
        String numero = c.assureNumeroAssurance();
        if (numero == null || numero.isBlank()) {
            throw new SaisieInvalide("N° assurance assuré obligatoire pour cette qualité d'assuré");
        }
        return fiche;
    }

    /** Refuse une saisie incohérente (invariants 1 et 2). */
    private static void verifier(Fiche fiche, LocalDate aujourdhui) {
        EtatDuPatient etat = fiche.etat();
        LocalDate evenement = fiche.dateEvenement();
        if ((etat.sortie() || etat.sejourLimite()) && evenement == null) {
            throw new RegleDePatientViolee("PATIENT_DATE_EVENEMENT_REQUISE",
                    "La date de l'évènement est obligatoire pour l'état " + etat);
        }
        if (evenement != null && evenement.isBefore(fiche.dateAdmission())) {
            throw new RegleDePatientViolee("PATIENT_DATE_EVENEMENT_AVANT_ADMISSION",
                    "La date de l'évènement ne peut pas précéder la date d'admission");
        }
        LocalDate naissance = fiche.identite().dateNaissance();
        if (naissance.isAfter(aujourdhui)) {
            throw new RegleDePatientViolee("PATIENT_DATE_NAISSANCE_FUTURE", "La date de naissance ne peut pas être future");
        }
        if (naissance.isAfter(fiche.dateAdmission())) {
            throw new RegleDePatientViolee("PATIENT_DATE_NAISSANCE_APRES_ADMISSION",
                    "La date de naissance ne peut pas suivre la date d'admission");
        }
    }

    /**
     * Le numéro d'assurance est unique par centre.
     *
     * @param detenteur patient qui porte déjà ce numéro dans le centre, s'il y en a un
     */
    public static void verifierNumeroLibre(Optional<PatientId> detenteur, Optional<PatientId> candidat) {
        if (detenteur.isPresent() && !detenteur.equals(candidat)) {
            throw new RegleDePatientViolee("PATIENT_NUMERO_ASSURANCE_DEJA_UTILISE",
                    "Numero assurance deja utilise pour ce centre");
        }
    }

    /**
     * L'attestation de droits est exigée sauf pour un vacancier : fournie dans la saisie ou déjà enregistrée.
     */
    public void verifierAttestation(boolean fournie, boolean dejaEnregistree) {
        if (!fiche.etat().vacancier() && !fournie && !dejaEnregistree) {
            throw new RegleDePatientViolee("PATIENT_ATTESTATION_REQUISE", "Attestation obligatoire pour un patient non-vacancier");
        }
    }

    private void tracer(Supplier<UUID> identifiants, TypeDeMouvement type, LocalDate dateEffet, EtatDuPatient precedent,
                        Instant maintenant) {
        Affectation a = fiche.affectation();
        mouvements.add(new MouvementDuPatient(identifiants.get(), id, centre, type, dateEffet, precedent, fiche.etat(),
                a.salleId(), a.creneauId(), a.generateurId(), a.jours().codes(), false, maintenant));
    }

    /** Mouvements nés de la dernière opération, à enregistrer avec le patient (vidés par l'appel). */
    public List<MouvementDuPatient> retirerLesMouvements() {
        List<MouvementDuPatient> retires = List.copyOf(mouvements);
        mouvements.clear();
        return retires;
    }

    public PatientId id() {
        return id;
    }

    public String code() {
        return code;
    }

    public CentreId centre() {
        return centre;
    }

    public boolean vacancier() {
        return vacancier;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Fiche fiche() {
        return fiche;
    }

    public EtatDuPatient etat() {
        return fiche.etat();
    }

    /** Dernier jour d'occupation de la place selon l'état et la date d'évènement (voir {@link EtatDuPatient}). */
    public Optional<LocalDate> dernierJourOccupe() {
        return fiche.etat().dernierJourOccupe(fiche.dateEvenement());
    }
}
