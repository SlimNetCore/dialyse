package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CarnetDeTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieDeTransporteurInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Transporteur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.TransporteurDejaExistant;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeTransporteur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gérer les transporteurs d'un centre (RF1) : créer, modifier, supprimer, lister, importer. Le centre est donné par l'appelant
 * (l'administration des référentiels a déjà contrôlé l'accès) ; chaque écriture est une unité de travail (rejointe si déjà ouverte).
 */
public class GererLesTransporteurs implements GestionDesTransporteurs {

    private static final UUID SANS_IDENTITE = new UUID(0, 0);

    private final CarnetDeTransporteurs carnet;
    private final LectureDesTransporteurs lecture;
    private final Identifiants identifiants;
    private final UniteDeTravail unite;

    public GererLesTransporteurs(CarnetDeTransporteurs carnet, LectureDesTransporteurs lecture, Identifiants identifiants,
                                 UniteDeTravail unite) {
        this.carnet = carnet;
        this.lecture = lecture;
        this.identifiants = identifiants;
        this.unite = unite;
    }

    private static FicheDeTransporteur fiche(Transporteur t) {
        return new FicheDeTransporteur(t.id(), t.nom(), t.telephone());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RefusDeTransporteur refus(SaisieDeTransporteurInvalide invalide) {
        return RefusDeTransporteur.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
    }

    private static void verifierLUnicite(Transporteur t, List<Transporteur> duCentre) {
        try {
            t.verifierLUnicite(duCentre);
        } catch (TransporteurDejaExistant existant) {
            throw RefusDeTransporteur.dejaExistant(existant.getMessage());
        }
    }

    @Override
    public FicheDeTransporteur creer(UUID centre, String nom, String telephone) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Transporteur t;
            try {
                t = Transporteur.referencer(identifiants.nouveau(), centreId, nom, telephone);
            } catch (SaisieDeTransporteurInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(t, carnet.duCentre(centreId));
            carnet.enregistrer(t);
            return fiche(t);
        });
    }

    @Override
    public FicheDeTransporteur modifier(UUID centre, UUID id, String nom, String telephone) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Transporteur existant = carnet.trouver(centreId, id).orElseThrow(RefusDeTransporteur::introuvable);
            Transporteur corrige;
            try {
                corrige = existant.corriger(nom, telephone);
            } catch (SaisieDeTransporteurInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(corrige, carnet.duCentre(centreId));
            carnet.remplacer(corrige);
            return fiche(corrige);
        });
    }

    @Override
    public void supprimer(UUID centre, UUID id) {
        CentreId centreId = new CentreId(centre);
        unite.executer(() -> {
            carnet.trouver(centreId, id).orElseThrow(RefusDeTransporteur::introuvable);
            long references = carnet.nombreDeReferences(centreId, id);
            if (references > 0) throw RefusDeTransporteur.utilise(references);
            carnet.supprimer(centreId, id);
            return null;
        });
    }

    @Override
    public PageDeTransporteurs lister(UUID centre, String recherche, int page, int taille) {
        return lecture.page(new CentreId(centre), recherche, page, taille);
    }

    @Override
    public BilanDImport importer(UUID centre, List<LigneAImporter> lignes, boolean ecrire) {
        CentreId centreId = new CentreId(centre);
        return unite.executer(() -> {
            Map<String, UUID> connus = new HashMap<>();
            for (Transporteur t : carnet.duCentre(centreId)) connus.putIfAbsent(t.cle(), t.id());

            Map<String, Integer> vusDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<Transporteur> aEcrire = new ArrayList<>();
            List<UUID> existants = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                Transporteur valide;
                try {
                    valide = Transporteur.referencer(SANS_IDENTITE, centreId, ligne.nom(), ligne.telephone());
                } catch (SaisieDeTransporteurInvalide invalide) {
                    invalide.anomalies().forEach(a -> anomalies.add(anomalie(ligne.numero(), a)));
                    continue;
                }
                Integer premiere = vusDansLeFichier.putIfAbsent(valide.cle(), ligne.numero());
                if (premiere != null) {
                    anomalies.add(new Anomalie(ligne.numero(), "nom", "DUPLICATE_IN_FILE",
                            "Doublon : même nom qu'à la ligne " + premiere + ".", Map.of("firstRow", String.valueOf(premiere))));
                    continue;
                }
                aEcrire.add(valide);
                existants.add(connus.get(valide.cle()));
            }

            int aMettreAJour = (int) existants.stream().filter(java.util.Objects::nonNull).count();
            int aCreer = aEcrire.size() - aMettreAJour;
            if (!anomalies.isEmpty()) return new BilanDImport(aCreer, aMettreAJour, anomalies, false);
            if (!ecrire) return new BilanDImport(aCreer, aMettreAJour, List.of(), false);

            for (int i = 0; i < aEcrire.size(); i++) {
                Transporteur saisi = aEcrire.get(i);
                UUID connu = existants.get(i);
                if (connu == null) {
                    carnet.enregistrer(Transporteur.referencer(identifiants.nouveau(), centreId, saisi.nom(), saisi.telephone()));
                } else {
                    carnet.remplacer(Transporteur.referencer(connu, centreId, saisi.nom(), saisi.telephone()));
                }
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
