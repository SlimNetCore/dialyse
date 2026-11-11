package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.AssureSurLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reprise des patients de l'outil de migration historique (ADR 0134, S6b-2) : mêmes tables {@code patients} et
 * {@code assure_patient}, mêmes colonnes. Une reprise n'efface jamais une donnée FACULTATIVE (civilité, lieu de naissance,
 * situation familiale, nombre d'enfants, profession, adresse, téléphones, e-mail, groupe sanguin, date de l'événement d'état,
 * observation, centre payeur, médecin traitant, salle, créneau, générateur, transporteurs) : seule une valeur renseignée du
 * fichier remplace celle de la fiche, et les jours de dialyse ne sont remplacés que si le fichier les fournit. En revanche les
 * données OBLIGATOIRES de la fiche sont remplacées sans condition par celles du fichier, comme le faisait l'outil historique : le
 * numéro d'assurance, le nom, le prénom, le sexe, la date de naissance, la date d'admission, le caractère vacancier, l'état et la
 * qualité d'assuré (le code, lui, est conservé s'il n'est pas renseigné). L'assuré principal se rattache comme à l'écran : le patient assuré lui-même n'a aucune
 * affectation principale et recopie sa propre fiche d'assuré ; l'ayant droit a pour unique affectation principale celle de son
 * assuré (commençant à l'admission). Les contrôles du fichier (doublons, références, assuré connu) sont ceux de l'appelant.
 */
public class ReprendreLesPatients implements RepriseDesPatients {

    private static final String ASSURE_LUI_MEME = "ASSURE_LUI_MEME";

    private final PatientsDeLaReprise patients;
    private final AssuresDuCentre assures;
    private final Identifiants identifiants;
    private final HorlogeDesCentres horloge;

    public ReprendreLesPatients(PatientsDeLaReprise patients, AssuresDuCentre assures, Identifiants identifiants,
                                HorlogeDesCentres horloge) {
        this.patients = patients;
        this.assures = assures;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    private static <T> T ou(T renseignee, T actuelle) {
        return renseignee != null ? renseignee : actuelle;
    }

    private static void exiger(String valeur, String libelle) {
        if (valeur == null || valeur.isBlank()) throw new SaisieInvalide(libelle + " obligatoire");
    }

    @Override
    public List<PatientDuCentre> duCentre(UUID centre) {
        return patients.duCentre(new CentreId(centre));
    }

    @Override
    public Optional<PatientRepris> trouver(UUID centre, UUID patient) {
        return patients.trouver(new CentreId(centre), patient).map(f -> new PatientRepris(f.id(), f.code(), f.nom(), f.prenom(),
                f.etat(), f.numeroAssurance(), f.centrePayeur()));
    }

    @Override
    public UUID reprendre(UUID centreDemande, UUID patient, ReportDuPatient report) {
        exiger(report.numeroAssurance(), "Numero assurance");
        exiger(report.nom(), "Nom");
        exiger(report.prenom(), "Prenom");
        exiger(report.sexe(), "Sexe");
        if (report.dateAdmission() == null) throw new SaisieInvalide("Date admission obligatoire");
        if (report.dateNaissance() == null) throw new SaisieInvalide("Date naissance obligatoire");

        boolean assureLuiMeme = ASSURE_LUI_MEME.equals(report.qualiteAssure());
        if (!assureLuiMeme) exiger(report.assureNumeroAssurance(), "Numero assurance de l'assure");

        CentreId centre = new CentreId(centreDemande);
        Optional<Fiche> existante = patient == null ? Optional.empty() : patients.trouver(centre, patient);
        UUID id = existante.map(Fiche::id)
                .orElseGet(() -> patient != null && !patients.identifiantPris(patient) ? patient : identifiants.nouveau());

        Fiche fusionnee = fusionner(id, existante.orElse(null), report);
        Fiche fiche = assureLuiMeme
                ? fusionnee.avecLAssure(report.numeroAssurance(), new AssureSurLaFiche(fusionnee.sexe(), fusionnee.nom(),
                        fusionnee.prenom(), fusionnee.dateNaissance(), fusionnee.telPersonnel(), fusionnee.adresse(),
                        fusionnee.groupeSanguin(), fusionnee.telMobile(), fusionnee.telBureau()))
                : fusionnee.avecLAssure(report.assureNumeroAssurance(), fusionnee.assure());

        if (existante.isPresent()) {
            patients.mettreAJour(centre, fiche);
        } else {
            patients.creer(centre, fiche, horloge.maintenant());
        }
        rattacherLAssure(centre, fiche, assureLuiMeme);
        return id;
    }

    /** Comme à l'écran : l'assuré principal est l'unique affectation principale du patient. */
    private void rattacherLAssure(CentreId centre, Fiche fiche, boolean assureLuiMeme) {
        PatientId patient = new PatientId(fiche.id());
        if (assureLuiMeme) {
            assures.retirerLePrincipal(centre, patient);
            return;
        }
        String numero = fiche.assureNumeroAssurance();
        Optional<AssuresDuCentre.Affectation> principale = assures.principalActuel(centre, patient);
        if (principale.isEmpty() || !numero.equalsIgnoreCase(principale.get().numeroAssurance())) {
            assures.retirerLePrincipal(centre, patient);
            assures.ajouterUneAffectationPrincipale(centre, patient, numero, fiche.dateAdmission());
        }
    }

    /** Seules les valeurs renseignées remplacent celles de la fiche existante (vide à la création). */
    private Fiche fusionner(UUID id, Fiche e, ReportDuPatient r) {
        boolean nouvelle = e == null;
        return new Fiche(id, ou(r.code(), nouvelle ? identifiants.nouveauCodePatient() : e.code()), r.numeroAssurance(), r.nom(),
                r.prenom(), r.sexe(), r.dateNaissance(), r.dateAdmission(), r.vacancier(), r.etat(), r.qualiteAssure(),
                ou(r.civilite(), nouvelle ? null : e.civilite()), ou(r.lieuNaissance(), nouvelle ? null : e.lieuNaissance()),
                ou(r.situationFamiliale(), nouvelle ? null : e.situationFamiliale()),
                ou(r.nombreEnfants(), nouvelle ? 0 : e.nombreEnfants()), ou(r.profession(), nouvelle ? null : e.profession()),
                ou(r.adresse(), nouvelle ? null : e.adresse()), ou(r.telPersonnel(), nouvelle ? null : e.telPersonnel()),
                ou(r.telMobile(), nouvelle ? null : e.telMobile()), ou(r.telBureau(), nouvelle ? null : e.telBureau()),
                ou(r.email(), nouvelle ? null : e.email()), ou(r.groupeSanguin(), nouvelle ? null : e.groupeSanguin()),
                ou(r.dateEvenementEtat(), nouvelle ? null : e.dateEvenementEtat()),
                ou(r.observation(), nouvelle ? null : e.observation()), ou(r.centrePayeur(), nouvelle ? null : e.centrePayeur()),
                ou(r.medecinTraitant(), nouvelle ? null : e.medecinTraitant()), ou(r.salle(), nouvelle ? null : e.salle()),
                ou(r.creneau(), nouvelle ? null : e.creneau()), ou(r.generateur(), nouvelle ? null : e.generateur()),
                ou(r.transporteurAller(), nouvelle ? null : e.transporteurAller()),
                ou(r.transporteurRetour(), nouvelle ? null : e.transporteurRetour()),
                r.jours() != null ? copie(r.jours()) : nouvelle ? Set.of() : e.jours(), null,
                nouvelle ? AssureSurLaFiche.vide() : e.assure());
    }

    private static Set<DayOfWeek> copie(Set<DayOfWeek> jours) {
        return jours.isEmpty() ? Set.of() : EnumSet.copyOf(jours);
    }
}
