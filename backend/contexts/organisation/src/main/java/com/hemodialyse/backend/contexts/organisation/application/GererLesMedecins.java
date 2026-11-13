package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.organisation.domain.ElementIntrouvable;
import com.hemodialyse.backend.contexts.organisation.domain.Medecin;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinDejaExistant;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinId;
import com.hemodialyse.backend.contexts.organisation.domain.MedecinRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeMedecin;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeMedecinInvalide;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeMedecin;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gérer les médecins d'un centre (PL11-3, ADR 0159) : créer, modifier, désactiver, lister, importer. Le centre est donné par l'appelant
 * (l'administration des référentiels ou la route d'Organisation a déjà contrôlé l'accès) ; chaque écriture est une unité de travail.
 * <p>
 * D5 : désactiver un médecin utilisé est autorisé, il n'y a donc aucun contrôle d'usage. D6 : créer ou réimporter un médecin de même
 * nom et prénom qu'un médecin désactivé le réactive. D7 : une seule règle d'unicité par centre, pour l'administration comme pour la
 * route d'Organisation.
 */
public class GererLesMedecins implements GestionDesMedecins {

    private final MedecinRepository medecins;
    private final GenerateurDIdentifiants identifiants;
    private final UniteDeTravail unite;

    public GererLesMedecins(MedecinRepository medecins, GenerateurDIdentifiants identifiants, UniteDeTravail unite) {
        this.medecins = medecins;
        this.identifiants = identifiants;
        this.unite = unite;
    }

    private static FicheDeMedecin fiche(Medecin m) {
        return new FicheDeMedecin(m.id().valeur(), m.nom(), m.prenom(), m.specialite());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static SaisieDeMedecin controler(String nom, String prenom, String specialite) {
        try {
            return SaisieDeMedecin.controler(nom, prenom, specialite);
        } catch (SaisieDeMedecinInvalide invalide) {
            throw RefusDeMedecin.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
        }
    }

    private static List<Medecin> proposes(List<Medecin> tous) {
        return tous.stream().filter(Medecin::actif).toList();
    }

    private static void verifierLUnicite(Medecin m, List<Medecin> tous) {
        try {
            m.verifierLUnicite(proposes(tous));
        } catch (MedecinDejaExistant existant) {
            throw RefusDeMedecin.dejaExistant(existant.getMessage());
        }
    }

    private static Optional<Medecin> desactiveDeMemeCle(SaisieDeMedecin saisie, List<Medecin> tous) {
        return tous.stream().filter(m -> !m.actif() && m.cle().equals(saisie.cle())).findFirst();
    }

    @Override
    public FicheDeMedecin creer(UUID centre, String nom, String prenom, String specialite) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            SaisieDeMedecin saisie = controler(nom, prenom, specialite);
            List<Medecin> tous = medecins.listerTousParCentre(centreId);
            Medecin nouveau = new Medecin(new MedecinId(identifiants.suivant()), centreId, saisie.nom(), saisie.prenom(),
                    saisie.specialite());
            verifierLUnicite(nouveau, tous);
            Optional<Medecin> desactive = desactiveDeMemeCle(saisie, tous);
            if (desactive.isPresent()) {
                Medecin retrouve = desactive.get();
                retrouve.reactiver(saisie);
                medecins.sauvegarder(retrouve);
                return fiche(retrouve);
            }
            medecins.sauvegarder(nouveau);
            return fiche(nouveau);
        });
    }

    @Override
    public FicheDeMedecin modifier(UUID centre, UUID id, String nom, String prenom, String specialite) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Medecin existant = medecinProposeDuCentre(centreId, id);
            SaisieDeMedecin saisie = controler(nom, prenom, specialite);
            existant.corriger(saisie);
            verifierLUnicite(existant, medecins.listerTousParCentre(centreId));
            medecins.sauvegarder(existant);
            return fiche(existant);
        });
    }

    private Medecin medecinProposeDuCentre(CentreId centreId, UUID id) {
        return medecins.trouver(new MedecinId(id)).filter(m -> m.centreId().equals(centreId)).filter(Medecin::actif)
                .orElseThrow(RefusDeMedecin::introuvable);
    }

    @Override
    public void desactiver(UUID centre, UUID id) {
        CentreId centreId = new CentreId(centre);
        unite.executer(() -> {
            Medecin existant = medecinProposeDuCentre(centreId, id);
            existant.desactiver();
            medecins.sauvegarder(existant);
            return null;
        });
    }

    /**
     * Route d'Organisation : correction partielle (un champ absent est conservé), sous la même règle d'unicité (D7). Un médecin
     * désactivé peut être corrigé (comportement conservé).
     */
    public void ajuster(CentreId centreId, MedecinId id, String nom, String prenom, String specialite) {
        unite.executer(() -> {
            Medecin m = medecins.trouver(id).filter(x -> x.centreId().equals(centreId))
                    .orElseThrow(() -> new ElementIntrouvable("Medecin inconnu"));
            m.modifier(nom, prenom, specialite);
            if (m.actif()) verifierLUnicite(m, medecins.listerTousParCentre(centreId));
            medecins.sauvegarder(m);
            return null;
        });
    }

    /** Route d'Organisation : désactivation idempotente d'un médecin du centre, utilisé ou non (D5). */
    public void desactiverSansEchec(CentreId centreId, MedecinId id) {
        unite.executer(() -> {
            Medecin m = medecins.trouver(id).filter(x -> x.centreId().equals(centreId))
                    .orElseThrow(() -> new ElementIntrouvable("Medecin inconnu"));
            m.desactiver();
            medecins.sauvegarder(m);
            return null;
        });
    }

    @Override
    public PageDeMedecins lister(UUID centre, String recherche, int page, int taille) {
        MedecinRepository.PageDeMedecins p = medecins.page(new CentreId(centre), recherche, page, taille);
        return new PageDeMedecins(p.lignes().stream().map(GererLesMedecins::fiche).toList(), p.total(), page, taille);
    }

    @Override
    public List<FicheDeMedecin> proposes(UUID centre) {
        return proposes(medecins.listerTousParCentre(new CentreId(centre))).stream().map(GererLesMedecins::fiche).toList();
    }

    @Override
    public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            List<Medecin> tous = medecins.listerTousParCentre(centreId);
            Map<String, Medecin> connus = new HashMap<>();
            proposes(tous).forEach(m -> connus.putIfAbsent(m.cle(), m));
            tous.stream().filter(m -> !m.actif()).forEach(m -> connus.putIfAbsent(m.cle(), m));

            Map<String, Integer> vusDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<SaisieDeMedecin> aEcrire = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                SaisieDeMedecin valide;
                try {
                    valide = SaisieDeMedecin.controler(ligne.nom(), ligne.prenom(), ligne.specialite());
                } catch (SaisieDeMedecinInvalide invalide) {
                    invalide.anomalies().forEach(a -> anomalies.add(anomalie(ligne.numero(), a)));
                    continue;
                }
                Integer premiere = vusDansLeFichier.putIfAbsent(valide.cle(), ligne.numero());
                if (premiere != null) {
                    anomalies.add(new Anomalie(ligne.numero(), "nom", "DUPLICATE_IN_FILE",
                            "Doublon : même nom + prénom qu'à la ligne " + premiere + ".", Map.of("firstRow", String.valueOf(premiere))));
                    continue;
                }
                aEcrire.add(valide);
            }

            int aMettreAJour = (int) aEcrire.stream().filter(s -> connus.containsKey(s.cle())).count();
            int aCreer = aEcrire.size() - aMettreAJour;
            if (!anomalies.isEmpty()) return new BilanDImport(aCreer, aMettreAJour, anomalies, false);
            if (!ecrire) return new BilanDImport(aCreer, aMettreAJour, List.of(), false);

            for (SaisieDeMedecin saisie : aEcrire) {
                Medecin connu = connus.get(saisie.cle());
                if (connu == null) {
                    medecins.sauvegarder(new Medecin(new MedecinId(identifiants.suivant()), centreId, saisie.nom(), saisie.prenom(),
                            saisie.specialite()));
                } else {
                    connu.reactiver(saisie);
                    medecins.sauvegarder(connu);
                }
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
