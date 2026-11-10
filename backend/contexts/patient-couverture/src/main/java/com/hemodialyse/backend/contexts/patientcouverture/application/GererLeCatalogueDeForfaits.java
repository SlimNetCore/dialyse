package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Forfait;
import com.hemodialyse.backend.contexts.patientcouverture.domain.ForfaitDejaExistant;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieDeForfaitInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.UsageDuForfait;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gérer le catalogue de forfaits d'un centre (RF2) : créer, modifier, supprimer, lister, importer. Le centre est donné par
 * l'appelant (l'administration des référentiels a déjà contrôlé l'accès) ; chaque écriture est une unité de travail (rejointe si
 * déjà ouverte). La suppression d'un forfait utilisé est refusée : les références sont additionnées auprès de chaque propriétaire
 * ({@link UsageDuForfait}).
 */
public class GererLeCatalogueDeForfaits implements GestionDuCatalogueDeForfaits {

    private static final UUID SANS_IDENTITE = new UUID(0, 0);

    private final CatalogueDeForfaits catalogue;
    private final LectureDuCatalogueDeForfaits lecture;
    private final List<UsageDuForfait> usages;
    private final Identifiants identifiants;
    private final UniteDeTravail unite;

    public GererLeCatalogueDeForfaits(CatalogueDeForfaits catalogue, LectureDuCatalogueDeForfaits lecture, List<UsageDuForfait> usages,
                                      Identifiants identifiants, UniteDeTravail unite) {
        this.catalogue = catalogue;
        this.lecture = lecture;
        this.usages = List.copyOf(usages);
        this.identifiants = identifiants;
        this.unite = unite;
    }

    private static FicheDeForfait fiche(Forfait f) {
        return new FicheDeForfait(f.id(), f.code(), f.libelle(), f.prix());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RefusDeForfait refus(SaisieDeForfaitInvalide invalide) {
        return RefusDeForfait.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
    }

    private static void verifierLUnicite(Forfait f, List<Forfait> duCentre) {
        try {
            f.verifierLUnicite(duCentre);
        } catch (ForfaitDejaExistant existant) {
            throw RefusDeForfait.dejaExistant(existant.getMessage());
        }
    }

    @Override
    public FicheDeForfait creer(UUID centre, String code, String libelle, String prix) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Forfait f;
            try {
                f = Forfait.referencer(identifiants.nouveau(), centreId, code, libelle, prix);
            } catch (SaisieDeForfaitInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(f, catalogue.duCentre(centreId));
            catalogue.enregistrer(f);
            return fiche(f);
        });
    }

    @Override
    public FicheDeForfait modifier(UUID centre, UUID id, String code, String libelle, String prix) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Forfait existant = catalogue.trouver(centreId, id).orElseThrow(RefusDeForfait::introuvable);
            Forfait corrige;
            try {
                corrige = existant.corriger(code, libelle, prix);
            } catch (SaisieDeForfaitInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(corrige, catalogue.duCentre(centreId));
            catalogue.remplacer(corrige);
            return fiche(corrige);
        });
    }

    @Override
    public void supprimer(UUID centre, UUID id) {
        CentreId centreId = new CentreId(centre);
        unite.executer(() -> {
            catalogue.trouver(centreId, id).orElseThrow(RefusDeForfait::introuvable);
            long references = usages.stream().mapToLong(u -> u.nombreDeReferences(centre, id)).sum();
            if (references > 0) throw RefusDeForfait.utilise(references);
            catalogue.supprimer(centreId, id);
            return null;
        });
    }

    @Override
    public PageDeForfaits lister(UUID centre, String recherche, int page, int taille) {
        return lecture.page(new CentreId(centre), recherche, page, taille);
    }

    @Override
    public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Map<String, UUID> connus = new HashMap<>();
            for (Forfait f : catalogue.duCentre(centreId)) connus.putIfAbsent(f.cle(), f.id());

            Map<String, Integer> vusDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<Forfait> aEcrire = new ArrayList<>();
            List<UUID> existants = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                Forfait valide;
                try {
                    valide = Forfait.referencer(SANS_IDENTITE, centreId, ligne.code(), ligne.libelle(), ligne.prix());
                } catch (SaisieDeForfaitInvalide invalide) {
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
                existants.add(connus.get(valide.cle()));
            }

            int aMettreAJour = (int) existants.stream().filter(Objects::nonNull).count();
            int aCreer = aEcrire.size() - aMettreAJour;
            if (!anomalies.isEmpty()) return new BilanDImport(aCreer, aMettreAJour, anomalies, false);
            if (!ecrire) return new BilanDImport(aCreer, aMettreAJour, List.of(), false);

            for (int i = 0; i < aEcrire.size(); i++) {
                Forfait saisi = aEcrire.get(i);
                UUID connu = existants.get(i);
                if (connu == null) {
                    catalogue.enregistrer(Forfait.reconstituer(identifiants.nouveau(), centreId, saisi.code(), saisi.libelle(), saisi.prix()));
                } else {
                    catalogue.remplacer(Forfait.reconstituer(connu, centreId, saisi.code(), saisi.libelle(), saisi.prix()));
                }
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
