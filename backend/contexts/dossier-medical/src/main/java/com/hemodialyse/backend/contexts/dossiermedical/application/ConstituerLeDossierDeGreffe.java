package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DonneursVivants;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutClinique;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Constitue le dossier de synthèse « préparation à la greffe rénale » du patient (RG-MED-095) et le publie à la plateforme qui le rend en
 * PDF. Garde du patient dans le cas d'usage (404, DM7 KB-MED-86/97). Mêmes contenus et mêmes ordres que le legacy ({@code GreffePdfService},
 * DM9, ADR 0101) : étapes triées par catégorie (tri stable, départage libellé, création, identifiant), sérologies par prélèvement
 * décroissant, antécédents ACTIFS par début décroissant, donneurs du plus récent au plus ancien, décisions par réunion décroissante.
 */
public class ConstituerLeDossierDeGreffe implements DossierDeGreffePublie {

    private final PatientsDuCentre patients;
    private final BilansPreGreffe bilans;
    private final EtapesDeBilan etapes;
    private final Serologies serologies;
    private final Antecedents antecedents;
    private final DonneursVivants donneurs;

    public ConstituerLeDossierDeGreffe(PatientsDuCentre patients, BilansPreGreffe bilans, EtapesDeBilan etapes, Serologies serologies,
                                       Antecedents antecedents, DonneursVivants donneurs) {
        this.patients = patients;
        this.bilans = bilans;
        this.etapes = etapes;
        this.serologies = serologies;
        this.antecedents = antecedents;
        this.donneurs = donneurs;
    }

    @Override
    public DossierDeGreffe duPatient(UUID centreDemande, UUID patientDemande) {
        CentreId centre = new CentreId(centreDemande);
        PatientId patient = new PatientId(patientDemande);
        if (!patients.existe(centre, patient)) throw new PatientIntrouvable();

        Bilan bilan = bilans.trouver(centre, patient).map(ConstituerLeDossierDeGreffe::bilan).orElse(null);
        List<Etape> lesEtapes = etapes.duPatient(centre, patient).stream()
                .sorted(Comparator.comparing(e -> e.categorie().name()))
                .map(ConstituerLeDossierDeGreffe::etape).toList();
        List<Serologie> lesSerologies = serologies.toutesDuPatient(centre, patient).stream()
                .map(s -> new Serologie(s.marqueur().name(), s.resultat().name(), s.datePrelevement())).toList();
        List<AntecedentActif> actifs = antecedents.tousDuPatient(centre, patient).stream()
                .filter(a -> a.statut() == StatutClinique.ACTIF)
                .map(ConstituerLeDossierDeGreffe::antecedent).toList();
        List<Donneur> lesDonneurs = donneurs.duPatient(centre, patient).stream()
                .map(d -> new Donneur(d.nom(), d.prenom(), d.lienDeParente().name(), d.groupeSanguin(), d.statutBilan().name(),
                        d.crossmatch().name())).toList();
        return new DossierDeGreffe(bilan, lesEtapes, lesSerologies, actifs, lesDonneurs);
    }

    private static Bilan bilan(BilanPreGreffe b) {
        return new Bilan(b.statut().name(), b.groupeSanguinConfirme(), b.typageHla(), b.praClasseI(), b.praClasseII(), b.contreIndications(),
                b.conclusionNephrologue(),
                b.decisions().stream().map(d -> new DecisionDeRcp(d.dateReunion(), d.avis().name(), d.compteRendu(), d.prochaineDateRevue())).toList());
    }

    private static Etape etape(EtapeDeBilan e) {
        return new Etape(e.categorie().name(), e.libelle(), e.statut().name(), e.dateRealisation(), e.resultat());
    }

    private static AntecedentActif antecedent(Antecedent a) {
        return new AntecedentActif(a.diagnostic() != null ? a.diagnostic().affichage() : a.libelleLibre());
    }
}
