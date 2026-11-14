package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationDroit;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.StatutPec;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CompteursDeCouverture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Double en mémoire du langage publié {@code patientcouverture.v1} (PC5) : mêmes règles que les requêtes SQL des adapters, sur les
 * agrégats du domaine. Le contrat {@link LecturesPublieesContrat} prouve sa fidélité aux tables réelles.
 */
class LecturesPublieesEnMemoire implements FichesDesPatients, CouvertureDesPatients, CompteursDeCouverture,
        com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ProgrammationDesPatients,
        com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients {

    record Forfait(UUID centre, String code, String libelle, BigDecimal prix) {
    }

    private final Supplier<Collection<Patient>> patients;
    private final Supplier<Collection<PriseEnCharge>> pecs;
    private final Supplier<Collection<AttestationDroit>> attestations;
    private final Map<UUID, Forfait> forfaits;

    LecturesPublieesEnMemoire(Supplier<Collection<Patient>> patients, Supplier<Collection<PriseEnCharge>> pecs,
                              Supplier<Collection<AttestationDroit>> attestations, Map<UUID, Forfait> forfaits) {
        this.patients = patients;
        this.pecs = pecs;
        this.attestations = attestations;
        this.forfaits = forfaits;
    }

    private Optional<Patient> patient(UUID centre, UUID id) {
        return patients.get().stream().filter(p -> p.centre().valeur().equals(centre) && p.id().valeur().equals(id)).findFirst();
    }

    // ---- FichesDesPatients

    @Override
    public boolean existe(UUID centre, UUID patient) {
        return patient(centre, patient).isPresent();
    }

    @Override
    public Map<UUID, NomDuPatient> noms(UUID centre, Collection<UUID> demandes) {
        Map<UUID, NomDuPatient> noms = new HashMap<>();
        for (UUID id : demandes) {
            patient(centre, id).ifPresent(p -> noms.put(id, new NomDuPatient(p.fiche().identite().prenom(), p.fiche().identite().nom())));
        }
        return noms;
    }

    @Override
    public Optional<IdentiteDuPatient> identite(UUID centre, UUID patient) {
        return patient(centre, patient).map(p -> new IdentiteDuPatient(p.fiche().identite().nom(), p.fiche().identite().prenom(),
                p.fiche().identite().sexe(), p.fiche().identite().dateNaissance(),
                p.fiche().couverture().numeroAssurance() == null ? null : p.fiche().couverture().numeroAssurance().valeur()));
    }

    @Override
    public Optional<CarteDuPatient> carte(UUID centre, UUID patient) {
        return patient(centre, patient).map(p -> new CarteDuPatient(p.code(), p.fiche().identite().nom(),
                p.fiche().identite().prenom(), p.fiche().identite().sexe(), p.fiche().identite().dateNaissance(),
                p.fiche().identite().groupeSanguin(),
                p.fiche().couverture().numeroAssurance() == null ? null : p.fiche().couverture().numeroAssurance().valeur(),
                p.fiche().coordonnees().telMobile()));
    }

    @Override
    public Optional<UUID> medecinTraitant(UUID centre, UUID patient) {
        return patient(centre, patient).map(p -> p.fiche().affectation().medecinTraitantId());
    }

    private static final Comparator<Patient> PAR_IDENTIFIANT = Comparator.comparing((Patient p) -> p.id().valeur().toString());

    private List<Patient> patientsDuCentre(UUID centre) {
        return patients.get().stream().filter(p -> p.centre().valeur().equals(centre)).sorted(PAR_IDENTIFIANT).toList();
    }

    private static String numeroDe(Patient p) {
        return p.fiche().couverture().numeroAssurance() == null ? null : p.fiche().couverture().numeroAssurance().valeur();
    }

    @Override
    public Optional<UUID> parCode(UUID centre, String code) {
        return patientsDuCentre(centre).stream().filter(p -> p.code() != null && p.code().equals(code)).map(p -> p.id().valeur()).findFirst();
    }

    @Override
    public Optional<UUID> parNumeroDAssurance(UUID centre, String numero) {
        return patientsDuCentre(centre).stream().filter(p -> numeroDe(p) != null && numeroDe(p).equals(numero)).map(p -> p.id().valeur())
                .findFirst();
    }

    @Override
    public List<ReferencesDuPatient> references(UUID centre) {
        return patientsDuCentre(centre).stream().map(p -> new ReferencesDuPatient(p.id().valeur(), p.code(), numeroDe(p),
                p.fiche().couverture().assureNumeroAssurance())).toList();
    }

    @Override
    public Situation situation(UUID centre, UUID patient) {
        return patient(centre, patient).map(p -> p.etat().sortie() ? Situation.SORTI : Situation.EN_SUIVI)
                .orElse(Situation.INTROUVABLE);
    }

    // ---- ProgrammationDesPatients

    private static ProgrammationDuPatient programmationDe(Patient p) {
        var jours = p.fiche().affectation().jours();
        java.util.EnumSet<java.time.DayOfWeek> dialyse = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
        if (jours.lundi()) dialyse.add(java.time.DayOfWeek.MONDAY);
        if (jours.mardi()) dialyse.add(java.time.DayOfWeek.TUESDAY);
        if (jours.mercredi()) dialyse.add(java.time.DayOfWeek.WEDNESDAY);
        if (jours.jeudi()) dialyse.add(java.time.DayOfWeek.THURSDAY);
        if (jours.vendredi()) dialyse.add(java.time.DayOfWeek.FRIDAY);
        if (jours.samedi()) dialyse.add(java.time.DayOfWeek.SATURDAY);
        if (jours.dimanche()) dialyse.add(java.time.DayOfWeek.SUNDAY);
        return new ProgrammationDuPatient(p.id().valeur(), p.fiche().identite().sexe(), p.fiche().identite().dateNaissance(),
                p.fiche().dateAdmission(), p.fiche().suivi().enSommeil(), p.etat() == null ? null : p.etat().name(),
                p.fiche().dateEvenement(), dialyse);
    }

    @Override
    public List<ProgrammationDuPatient> duCentre(UUID centre) {
        return patientsDuCentre(centre).stream().map(LecturesPublieesEnMemoire::programmationDe).toList();
    }

    @Override
    public Optional<ProgrammationDuPatient> duPatient(UUID centre, UUID patient) {
        return patient(centre, patient).map(LecturesPublieesEnMemoire::programmationDe);
    }

    // ---- LotsDePatients

    private Optional<Patient> patientSansBorne(UUID id) {
        return patients.get().stream().filter(p -> p.id().valeur().equals(id)).findFirst();
    }

    @Override
    public Map<UUID, NomDuPatient> identitesDuCentre(UUID centre) {
        Map<UUID, NomDuPatient> identites = new java.util.LinkedHashMap<>();
        patientsDuCentre(centre).forEach(p -> identites.put(p.id().valeur(),
                new NomDuPatient(p.fiche().identite().prenom(), p.fiche().identite().nom())));
        return identites;
    }

    @Override
    public List<EtatCivilDuPatient> etatCivil(Collection<UUID> demandes) {
        return demandes.stream().distinct().map(this::patientSansBorne).flatMap(Optional::stream)
                .map(p -> new EtatCivilDuPatient(p.id().valeur(), p.fiche().identite().sexe(), p.fiche().identite().dateNaissance()))
                .toList();
    }

    @Override
    public List<PatientDuLot> parNomPuisPrenom(Collection<UUID> demandes) {
        List<Patient> tries = demandes.stream().distinct().map(this::patientSansBorne).flatMap(Optional::stream)
                .sorted(Comparator.comparing((Patient p) -> p.fiche().identite().nom(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(p -> p.fiche().identite().prenom(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PatientDuLot> lot = new java.util.ArrayList<>();
        int rang = 0;
        Patient precedent = null;
        for (Patient p : tries) {
            if (precedent == null || !java.util.Objects.equals(precedent.fiche().identite().nom(), p.fiche().identite().nom())
                    || !java.util.Objects.equals(precedent.fiche().identite().prenom(), p.fiche().identite().prenom())) rang++;
            lot.add(new PatientDuLot(p.id().valeur(), p.code(), p.fiche().identite().nom(), p.fiche().identite().prenom(), rang));
            precedent = p;
        }
        return lot;
    }

    @Override
    public List<PatientAFacturer> aFacturerDuCentre(UUID centre, Collection<UUID> demandes) {
        List<Patient> tries = demandes.stream().distinct().map(id -> patient(centre, id)).flatMap(Optional::stream)
                .sorted(Comparator.comparing((Patient p) -> p.fiche().identite().nom(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(p -> p.fiche().identite().prenom(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<PatientAFacturer> lot = new java.util.ArrayList<>();
        int rang = 0;
        Patient precedent = null;
        for (Patient p : tries) {
            if (precedent == null || !java.util.Objects.equals(precedent.fiche().identite().nom(), p.fiche().identite().nom())
                    || !java.util.Objects.equals(precedent.fiche().identite().prenom(), p.fiche().identite().prenom())) rang++;
            lot.add(new PatientAFacturer(p.id().valeur(), p.code(), p.fiche().identite().nom(), p.fiche().identite().prenom(),
                    p.etat() == null ? null : p.etat().name(), numeroDe(p), p.fiche().couverture().centrePayeurId(), rang));
            precedent = p;
        }
        return lot;
    }

    @Override
    public Map<UUID, UUID> centresPayeursDuCentre(UUID centre, Collection<UUID> demandes) {
        Map<UUID, UUID> payeurs = new java.util.LinkedHashMap<>();
        demandes.stream().distinct().map(id -> patient(centre, id)).flatMap(Optional::stream)
                .filter(p -> p.fiche().couverture().centrePayeurId() != null)
                .forEach(p -> payeurs.put(p.id().valeur(), p.fiche().couverture().centrePayeurId()));
        return payeurs;
    }

    // ---- CouvertureDesPatients

    private static UUID forfaitDe(PriseEnCharge p) {
        return p.accordForfait() != null ? p.accordForfait() : p.demandeForfait();
    }

    private static boolean couvre(LocalDate debut, LocalDate fin, LocalDate jour) {
        return debut != null && !jour.isBefore(debut) && (fin == null || !jour.isAfter(fin));
    }

    private static boolean periodeCouvre(PriseEnCharge p, LocalDate jour) {
        return couvre(p.accordDebut(), p.accordFin(), jour) || couvre(p.demandeDebut(), p.demandeFin(), jour);
    }

    private List<PriseEnCharge> pecsDuPatient(UUID centre, UUID patient) {
        return pecs.get().stream().filter(p -> p.centre().valeur().equals(centre) && p.patient().valeur().equals(patient)).toList();
    }

    private static final Comparator<PriseEnCharge> ORDRE = Comparator
            .comparing((PriseEnCharge p) -> p.accordForfait() != null ? 0 : 1)
            .thenComparing(PriseEnCharge::creeLe, Comparator.reverseOrder());

    @Override
    public Optional<ForfaitApplicable> forfaitApplicable(UUID centre, UUID patient, LocalDate date) {
        List<PriseEnCharge> avecForfait = pecsDuPatient(centre, patient).stream()
                .filter(p -> forfaitDe(p) != null && forfaits.containsKey(forfaitDe(p))
                        && forfaits.get(forfaitDe(p)).centre().equals(centre)).sorted(ORDRE).toList();
        Optional<PriseEnCharge> choisie = avecForfait.stream().filter(p -> periodeCouvre(p, date)).findFirst();
        if (choisie.isEmpty()) choisie = avecForfait.stream().findFirst();
        return choisie.map(p -> {
            Forfait f = forfaits.get(forfaitDe(p));
            return new ForfaitApplicable(forfaitDe(p), f.code(), f.libelle(), f.prix());
        });
    }

    @Override
    public List<PriseEnChargeTarifee> prisesEnChargeTarifees(UUID centre, UUID patient) {
        return pecsDuPatient(centre, patient).stream()
                .filter(p -> forfaitDe(p) != null && forfaits.containsKey(forfaitDe(p)) && forfaits.get(forfaitDe(p)).centre().equals(centre))
                .map(p -> {
                    Forfait f = forfaits.get(forfaitDe(p));
                    return new PriseEnChargeTarifee(forfaitDe(p), f.libelle(), f.prix(), p.accordForfait() != null, p.accordDebut(),
                            p.accordFin(), p.demandeDebut(), p.demandeFin(), p.creeLe());
                }).toList();
    }

    @Override
    public Optional<CauseDeNonFacturation> causeDeNonFacturation(UUID centre, UUID patient, LocalDate jour) {
        List<PriseEnCharge> couvrantes = pecsDuPatient(centre, patient).stream()
                .filter(p -> forfaitDe(p) != null && periodeCouvre(p, jour)).toList();
        if (couvrantes.isEmpty()) return Optional.of(CauseDeNonFacturation.AUCUNE_PEC_COUVRANTE);
        List<PriseEnCharge> validees = couvrantes.stream().filter(p -> p.statut() == StatutPec.VALIDEE).toList();
        if (validees.isEmpty()) return Optional.of(CauseDeNonFacturation.PEC_NON_VALIDEE);
        boolean attestee = attestations.get().stream().anyMatch(a -> a.centre().valeur().equals(centre)
                && a.patient().valeur().equals(patient) && a.couvre(jour));
        return attestee ? Optional.empty() : Optional.of(CauseDeNonFacturation.ATTESTATION_ABSENTE);
    }

    @Override
    public boolean pecValidee(UUID centre, UUID patient, UUID pec) {
        return pecs.get().stream().anyMatch(p -> p.id().equals(pec) && p.centre().valeur().equals(centre)
                && p.patient().valeur().equals(patient) && p.statut() == StatutPec.VALIDEE);
    }

    // ---- CompteursDeCouverture

    private static boolean creeDans(Instant creeLe, LocalDate du, LocalDate au) {
        if (du == null || au == null) return true;
        return !creeLe.isBefore(du.atStartOfDay(ZoneOffset.UTC).toInstant()) && !creeLe.isAfter(au.atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Override
    public long prisesEnCharge(UUID centre, StatutDePec statut, LocalDate du, LocalDate au) {
        return pecs.get().stream().filter(p -> p.centre().valeur().equals(centre) && p.statut().name().equals(statut.name()))
                .filter(p -> creeDans(p.creeLe(), du, au)).count();
    }

    @Override
    public long prisesEnChargeValideesAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil) {
        return pecs.get().stream().filter(p -> p.centre().valeur().equals(centre) && p.statut() == StatutPec.VALIDEE
                && p.demandeFin() != null && !p.demandeFin().isBefore(aujourdhui) && !p.demandeFin().isAfter(seuil)).count();
    }

    @Override
    public long attestations(UUID centre, LocalDate du, LocalDate au) {
        return attestations.get().stream().filter(a -> a.centre().valeur().equals(centre)).filter(a -> creeDans(a.creeLe(), du, au))
                .count();
    }

    @Override
    public long attestationsAEcheance(UUID centre, LocalDate aujourdhui, LocalDate seuil) {
        return attestations.get().stream().filter(a -> a.centre().valeur().equals(centre) && !a.fin().isBefore(aujourdhui)
                && !a.fin().isAfter(seuil)).count();
    }

    @Override
    public long patientsCrees(UUID centre, LocalDate du, LocalDate au) {
        return patients.get().stream().filter(p -> p.centre().valeur().equals(centre)).filter(p -> creeDans(p.creeLe(), du, au)).count();
    }

    @Override
    public long patientsPresentsLe(UUID centre, LocalDate jour) {
        return patients.get().stream().filter(p -> p.centre().valeur().equals(centre)).filter(p -> {
            LocalDate admission = p.fiche().dateAdmission();
            if (admission != null && admission.isAfter(jour)) return false;
            EtatDuPatient etat = p.etat();
            LocalDate evenement = p.fiche().dateEvenement();
            if (etat == null || (!etat.sortie() && !etat.sejourLimite())) return true;
            if (etat.sejourLimite()) return evenement == null || !evenement.isBefore(jour);
            if (etat == EtatDuPatient.TRANSFERE || etat == EtatDuPatient.GUERRI) return evenement != null && !evenement.isBefore(jour);
            return evenement != null && evenement.isAfter(jour);
        }).count();
    }
}
