package com.hemodialyse.backend.contexts.organisation.application;

import com.hemodialyse.backend.contexts.organisation.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeSalle;
import com.hemodialyse.backend.contexts.organisation.domain.SaisieDeSalleInvalide;
import com.hemodialyse.backend.contexts.organisation.domain.Salle;
import com.hemodialyse.backend.contexts.organisation.domain.SalleDejaExistante;
import com.hemodialyse.backend.contexts.organisation.domain.SalleId;
import com.hemodialyse.backend.contexts.organisation.domain.SalleRepository;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeSalle;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.UsageDeLaSalle;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Gérer les salles d'un centre (PL11-4, ADR 0160) : créer, modifier, supprimer ou désactiver, lister, importer. Le centre est donné par
 * l'appelant (l'administration des référentiels a déjà contrôlé l'accès) ; chaque écriture est une unité de travail.
 * <p>
 * D3 : le contrôle d'usage additionne les références de tous les propriétaires ({@link UsageDeLaSalle}). D4 : une salle référencée est
 * désactivée au lieu d'être supprimée. D8 : l'isolement et la capacité non précisés sont conservés. D10 : si un propriétaire ne sait pas
 * répondre, la suppression est refusée.
 */
public class GererLesSalles implements GestionDesSalles {

    private final SalleRepository salles;
    private final GenerateurDIdentifiants identifiants;
    private final UniteDeTravail unite;
    private final List<UsageDeLaSalle> usages;

    public GererLesSalles(SalleRepository salles, GenerateurDIdentifiants identifiants, UniteDeTravail unite, List<UsageDeLaSalle> usages) {
        this.salles = salles;
        this.identifiants = identifiants;
        this.unite = unite;
        this.usages = usages;
    }

    private static FicheDeSalle fiche(Salle s) {
        return new FicheDeSalle(s.id().valeur(), s.code(), s.nom(), s.isolement() ? "OUI" : "NON", s.capacite());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static SaisieDeSalle controler(String code, String nom, String isolement, String capacite) {
        try {
            return SaisieDeSalle.controler(code, nom, isolement, capacite);
        } catch (SaisieDeSalleInvalide invalide) {
            throw RefusDeSalle.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
        }
    }

    private static List<Salle> proposees(List<Salle> toutes) {
        return toutes.stream().filter(Salle::actif).toList();
    }

    private static void verifierLUnicite(Salle s, List<Salle> toutes) {
        try {
            s.verifierLUnicite(proposees(toutes));
        } catch (SalleDejaExistante existante) {
            throw RefusDeSalle.dejaExistante(existante.getMessage());
        }
    }

    @Override
    public FicheDeSalle creer(UUID centre, String code, String nom, String isolement, String capacite) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            SaisieDeSalle saisie = controler(code, nom, isolement, capacite);
            List<Salle> toutes = salles.listerTousParCentre(centreId);
            Optional<Salle> desactivee = toutes.stream().filter(s -> !s.actif() && s.cle().equals(saisie.cle())).findFirst();
            Salle s = Salle.referencer(new SalleId(identifiants.suivant()), centreId, saisie);
            verifierLUnicite(s, toutes);
            if (desactivee.isPresent()) {
                Salle retrouvee = desactivee.get();
                retrouvee.reactiver(saisie);
                salles.sauvegarder(retrouvee);
                return fiche(retrouvee);
            }
            salles.sauvegarder(s);
            return fiche(s);
        });
    }

    @Override
    public FicheDeSalle modifier(UUID centre, UUID id, String code, String nom, String isolement, String capacite) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Salle existante = salles.trouver(centreId, new SalleId(id)).filter(Salle::actif).orElseThrow(RefusDeSalle::introuvable);
            existante.corriger(controler(code, nom, isolement, capacite));
            verifierLUnicite(existante, salles.listerTousParCentre(centreId));
            salles.sauvegarder(existante);
            return fiche(existante);
        });
    }

    @Override
    public boolean supprimer(UUID centre, UUID id) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Salle existante = salles.trouver(centreId, new SalleId(id)).filter(Salle::actif).orElseThrow(RefusDeSalle::introuvable);
            if (nombreDeReferences(id) > 0) {
                existante.desactiver();
                salles.sauvegarder(existante);
                return true;
            }
            salles.supprimer(centreId, existante.id());
            return false;
        });
    }

    /** D3 + D10 : toutes les références, et le moindre échec d'un propriétaire interdit de conclure à « aucune ». */
    private long nombreDeReferences(UUID salle) {
        long total = 0;
        for (UsageDeLaSalle usage : usages) {
            try {
                total += usage.nombreDeReferences(salle);
            } catch (RuntimeException e) {
                throw RefusDeSalle.controleDUsageImpossible();
            }
        }
        return total;
    }

    @Override
    public PageDeSalles lister(UUID centre, String recherche, int page, int taille) {
        SalleRepository.PageDeSalles p = salles.page(new CentreId(centre), recherche, page, taille);
        return new PageDeSalles(p.lignes().stream().map(GererLesSalles::fiche).toList(), p.total(), page, taille);
    }

    @Override
    public List<FicheDeSalle> proposees(UUID centre) {
        return salles.listerParCentre(new CentreId(centre)).stream().map(GererLesSalles::fiche).toList();
    }

    @Override
    public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            List<Salle> toutes = salles.listerTousParCentre(centreId);
            Map<String, Salle> connues = new HashMap<>();
            proposees(toutes).forEach(s -> connues.putIfAbsent(s.cle(), s));
            toutes.stream().filter(s -> !s.actif()).forEach(s -> connues.putIfAbsent(s.cle(), s));

            Map<String, Integer> vuesDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<SaisieDeSalle> aEcrire = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                SaisieDeSalle valide;
                try {
                    valide = SaisieDeSalle.controler(ligne.code(), ligne.nom(), ligne.isolement(), ligne.capacite());
                } catch (SaisieDeSalleInvalide invalide) {
                    invalide.anomalies().forEach(a -> anomalies.add(anomalie(ligne.numero(), a)));
                    continue;
                }
                Integer premiere = vuesDansLeFichier.putIfAbsent(valide.cle(), ligne.numero());
                if (premiere != null) {
                    anomalies.add(new Anomalie(ligne.numero(), "code", "DUPLICATE_IN_FILE",
                            "Doublon : même code qu'à la ligne " + premiere + ".", Map.of("firstRow", String.valueOf(premiere))));
                    continue;
                }
                aEcrire.add(valide);
            }

            int aMettreAJour = (int) aEcrire.stream().filter(s -> connues.containsKey(s.cle())).count();
            int aCreer = aEcrire.size() - aMettreAJour;
            if (!anomalies.isEmpty()) return new BilanDImport(aCreer, aMettreAJour, anomalies, false);
            if (!ecrire) return new BilanDImport(aCreer, aMettreAJour, List.of(), false);

            for (SaisieDeSalle saisie : aEcrire) {
                Salle connue = connues.get(saisie.cle());
                if (connue == null) {
                    salles.sauvegarder(Salle.referencer(new SalleId(identifiants.suivant()), centreId, saisie));
                } else {
                    connue.reactiver(saisie);
                    salles.sauvegarder(connue);
                }
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
