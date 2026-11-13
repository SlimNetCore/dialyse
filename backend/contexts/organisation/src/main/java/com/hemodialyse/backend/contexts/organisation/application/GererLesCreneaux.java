package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.organisation.domain.Creneau;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauDejaExistant;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauId;
import com.hemodialyse.backend.contexts.organisation.domain.CreneauRepository;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeCreneau;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeCreneauInvalide;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeCreneau;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDuCreneau;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gérer les créneaux d'un centre (PL11-2, ADR 0158) : créer, modifier, supprimer ou désactiver, lister, importer. Le centre est donné
 * par l'appelant (l'administration des référentiels a déjà contrôlé l'accès) ; chaque écriture est une unité de travail.
 * <p>
 * D3 : le contrôle d'usage additionne les références de tous les propriétaires ({@link UsageDuCreneau}). D4 : un créneau référencé est
 * désactivé au lieu d'être supprimé. D10 : si un propriétaire ne sait pas répondre, la suppression est refusée.
 */
public class GererLesCreneaux implements GestionDesCreneaux {

    private final CreneauRepository creneaux;
    private final GenerateurDIdentifiants identifiants;
    private final UniteDeTravail unite;
    private final List<UsageDuCreneau> usages;

    public GererLesCreneaux(CreneauRepository creneaux, GenerateurDIdentifiants identifiants, UniteDeTravail unite,
                            List<UsageDuCreneau> usages) {
        this.creneaux = creneaux;
        this.identifiants = identifiants;
        this.unite = unite;
        this.usages = usages;
    }

    private static FicheDeCreneau fiche(Creneau c) {
        return new FicheDeCreneau(c.id().valeur(), c.code(), c.libelle());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RefusDeCreneau refus(SaisieDeCreneauInvalide invalide) {
        return RefusDeCreneau.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
    }

    private static SaisieDeCreneau controler(String code, String libelle) {
        try {
            return SaisieDeCreneau.controler(code, libelle);
        } catch (SaisieDeCreneauInvalide invalide) {
            throw refus(invalide);
        }
    }

    private static List<Creneau> proposes(List<Creneau> tous) {
        return tous.stream().filter(Creneau::actif).toList();
    }

    private static void verifierLUnicite(Creneau c, List<Creneau> tous) {
        try {
            c.verifierLUnicite(proposes(tous));
        } catch (CreneauDejaExistant existant) {
            throw RefusDeCreneau.dejaExistant(existant.getMessage());
        }
    }

    private static Optional<Creneau> desactiveDeMemeCode(SaisieDeCreneau saisie, List<Creneau> tous) {
        return tous.stream().filter(c -> !c.actif() && c.cle().equals(saisie.cle())).findFirst();
    }

    @Override
    public FicheDeCreneau creer(UUID centre, String code, String libelle) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            SaisieDeCreneau saisie = controler(code, libelle);
            List<Creneau> tous = creneaux.listerTousParCentre(centreId);
            Optional<Creneau> desactive = desactiveDeMemeCode(saisie, tous);
            Creneau c = Creneau.referencer(new CreneauId(identifiants.suivant()), centreId, saisie);
            verifierLUnicite(c, tous);
            if (desactive.isPresent()) {
                Creneau retrouve = desactive.get();
                retrouve.reactiver(saisie);
                creneaux.sauvegarder(retrouve);
                return fiche(retrouve);
            }
            creneaux.sauvegarder(c);
            return fiche(c);
        });
    }

    @Override
    public FicheDeCreneau modifier(UUID centre, UUID id, String code, String libelle) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Creneau existant = creneaux.trouver(centreId, new CreneauId(id)).filter(Creneau::actif)
                    .orElseThrow(RefusDeCreneau::introuvable);
            existant.corriger(controler(code, libelle));
            verifierLUnicite(existant, creneaux.listerTousParCentre(centreId));
            creneaux.sauvegarder(existant);
            return fiche(existant);
        });
    }

    @Override
    public boolean supprimer(UUID centre, UUID id) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Creneau existant = creneaux.trouver(centreId, new CreneauId(id)).filter(Creneau::actif)
                    .orElseThrow(RefusDeCreneau::introuvable);
            if (nombreDeReferences(id) > 0) {
                existant.desactiver();
                creneaux.sauvegarder(existant);
                return true;
            }
            creneaux.supprimer(centreId, existant.id());
            return false;
        });
    }

    /** D3 + D10 : toutes les références, et le moindre échec d'un propriétaire interdit de conclure à « aucune ». */
    private long nombreDeReferences(UUID creneau) {
        long total = 0;
        for (UsageDuCreneau usage : usages) {
            try {
                total += usage.nombreDeReferences(creneau);
            } catch (RuntimeException e) {
                throw RefusDeCreneau.controleDUsageImpossible();
            }
        }
        return total;
    }

    @Override
    public PageDeCreneaux lister(UUID centre, String recherche, int page, int taille) {
        CreneauRepository.PageDeCreneaux p = creneaux.page(new CentreId(centre), recherche, page, taille);
        return new PageDeCreneaux(p.lignes().stream().map(GererLesCreneaux::fiche).toList(), p.total(), page, taille);
    }

    @Override
    public List<FicheDeCreneau> proposes(UUID centre) {
        return creneaux.listerParCentre(new CentreId(centre)).stream().map(GererLesCreneaux::fiche).toList();
    }

    @Override
    public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            List<Creneau> tous = creneaux.listerTousParCentre(centreId);
            Map<String, Creneau> connus = new HashMap<>();
            proposes(tous).forEach(c -> connus.putIfAbsent(c.cle(), c));
            tous.stream().filter(c -> !c.actif()).forEach(c -> connus.putIfAbsent(c.cle(), c));

            Map<String, Integer> vusDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<SaisieDeCreneau> aEcrire = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                SaisieDeCreneau valide;
                try {
                    valide = SaisieDeCreneau.controler(ligne.code(), ligne.libelle());
                } catch (SaisieDeCreneauInvalide invalide) {
                    invalide.anomalies().forEach(a -> anomalies.add(anomalie(ligne.numero(), a)));
                    continue;
                }
                Integer premiere = vusDansLeFichier.putIfAbsent(valide.cle(), ligne.numero());
                if (premiere != null) {
                    anomalies.add(new Anomalie(ligne.numero(), "code", "DUPLICATE_IN_FILE",
                            "Doublon : même code qu'à la ligne " + premiere + ".", Map.of("firstRow", String.valueOf(premiere))));
                    continue;
                }
                aEcrire.add(valide);
            }

            int aMettreAJour = (int) aEcrire.stream().filter(s -> connus.containsKey(s.cle())).count();
            int aCreer = aEcrire.size() - aMettreAJour;
            if (!anomalies.isEmpty()) return new BilanDImport(aCreer, aMettreAJour, anomalies, false);
            if (!ecrire) return new BilanDImport(aCreer, aMettreAJour, List.of(), false);

            for (SaisieDeCreneau saisie : aEcrire) {
                Creneau connu = connus.get(saisie.cle());
                if (connu == null) {
                    creneaux.sauvegarder(Creneau.referencer(new CreneauId(identifiants.suivant()), centreId, saisie));
                } else {
                    connu.reactiver(saisie);
                    creneaux.sauvegarder(connu);
                }
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
