package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Agence;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AnomalieDeSaisie;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CaisseDAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentrePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Payeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PayeurDejaExistant;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Payeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieDePayeurInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDePayeur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gérer la chaîne des payeurs d'un centre (RF3a) : créer, modifier, supprimer, lister, importer les caisses d'assurance, les agences
 * et les centres payeurs. Le centre est donné par l'appelant (l'administration des référentiels a déjà contrôlé l'accès) ; chaque
 * écriture est une unité de travail (rejointe si déjà ouverte). La suppression d'un payeur est refusée tant qu'un payeur du niveau
 * inférieur ou un patient s'y rattache ({@link UsageDesPayeurs}). Une modification vérifie d'abord que le payeur existe dans le centre,
 * puis la saisie, puis l'unicité du code ; un import contrôle toutes ses lignes avant d'écrire quoi que ce soit.
 */
public class GererLesPayeurs implements GestionDesPayeurs {

    private static final UUID SANS_IDENTITE = new UUID(0, 0);

    private final Voie<?, ?> caisse;
    private final Voie<?, ?> agence;
    private final Voie<?, ?> centrePayeur;
    private final LectureDesPayeurs lecture;
    private final UsageDesPayeurs usage;
    private final Identifiants identifiants;
    private final UniteDeTravail unite;

    public GererLesPayeurs(Caisses caisses, Agences agences, CentresPayeurs centresPayeurs, LectureDesPayeurs lecture,
                           UsageDesPayeurs usage, Identifiants identifiants, UniteDeTravail unite) {
        this.caisse = new VoieDesCaisses(caisses);
        this.agence = new VoieDesAgences(agences, caisses);
        this.centrePayeur = new VoieDesCentresPayeurs(centresPayeurs, agences);
        this.lecture = lecture;
        this.usage = usage;
        this.identifiants = identifiants;
        this.unite = unite;
    }

    /** Un niveau de la chaîne : son port, ses règles de saisie et le port de son parent ({@code Q}). */
    private abstract static class Voie<P extends Payeur, Q extends Payeur> {
        final Payeurs<P> payeurs;

        Voie(Payeurs<P> payeurs) {
            this.payeurs = payeurs;
        }

        abstract List<Q> parents(CentreId centre);

        abstract P referencer(UUID id, CentreId centre, SaisieDePayeur saisie, List<Q> parents);

        abstract P corriger(P existant, SaisieDePayeur saisie, List<Q> parents);

        /** Les valeurs validées d'une ligne importée, portées par l'identité d'un payeur déjà connu ou nouveau. */
        abstract P sousIdentite(P valide, UUID id);

        abstract FicheDePayeur fiche(P payeur, List<Q> parents);

        static String libelle(Payeur parent) {
            return parent.code() + " · " + parent.nom();
        }

        static <Q extends Payeur> String libelleDuParent(UUID id, List<Q> parents) {
            return parents.stream().filter(p -> p.id().equals(id)).findFirst().map(Voie::libelle).orElse(null);
        }
    }

    private static final class VoieDesCaisses extends Voie<CaisseDAssurance, CaisseDAssurance> {
        VoieDesCaisses(Caisses caisses) {
            super(caisses);
        }

        @Override
        List<CaisseDAssurance> parents(CentreId centre) {
            return List.of();
        }

        @Override
        CaisseDAssurance referencer(UUID id, CentreId centre, SaisieDePayeur s, List<CaisseDAssurance> parents) {
            return CaisseDAssurance.referencer(id, centre, s.code(), s.nom(), s.typeCaisse());
        }

        @Override
        CaisseDAssurance corriger(CaisseDAssurance existante, SaisieDePayeur s, List<CaisseDAssurance> parents) {
            return existante.corriger(s.code(), s.nom(), s.typeCaisse());
        }

        @Override
        CaisseDAssurance sousIdentite(CaisseDAssurance v, UUID id) {
            return CaisseDAssurance.reconstituer(id, v.centre(), v.code(), v.nom(), v.type());
        }

        @Override
        FicheDePayeur fiche(CaisseDAssurance c, List<CaisseDAssurance> parents) {
            return new FicheDePayeur(c.id(), c.code(), c.nom(), c.type(), null, null, null);
        }
    }

    private static final class VoieDesAgences extends Voie<Agence, CaisseDAssurance> {
        private final Caisses caisses;

        VoieDesAgences(Agences agences, Caisses caisses) {
            super(agences);
            this.caisses = caisses;
        }

        @Override
        List<CaisseDAssurance> parents(CentreId centre) {
            return caisses.duCentre(centre);
        }

        @Override
        Agence referencer(UUID id, CentreId centre, SaisieDePayeur s, List<CaisseDAssurance> parents) {
            return Agence.referencer(id, centre, s.code(), s.nom(), s.parent(), parents);
        }

        @Override
        Agence corriger(Agence existante, SaisieDePayeur s, List<CaisseDAssurance> parents) {
            return existante.corriger(s.code(), s.nom(), s.parent(), parents);
        }

        @Override
        Agence sousIdentite(Agence v, UUID id) {
            return Agence.reconstituer(id, v.centre(), v.code(), v.nom(), v.caisse());
        }

        @Override
        FicheDePayeur fiche(Agence a, List<CaisseDAssurance> parents) {
            return new FicheDePayeur(a.id(), a.code(), a.nom(), null, null, a.caisse(), libelleDuParent(a.caisse(), parents));
        }
    }

    private static final class VoieDesCentresPayeurs extends Voie<CentrePayeur, Agence> {
        private final Agences agences;

        VoieDesCentresPayeurs(CentresPayeurs centres, Agences agences) {
            super(centres);
            this.agences = agences;
        }

        @Override
        List<Agence> parents(CentreId centre) {
            return agences.duCentre(centre);
        }

        @Override
        CentrePayeur referencer(UUID id, CentreId centre, SaisieDePayeur s, List<Agence> parents) {
            return CentrePayeur.referencer(id, centre, s.code(), s.nom(), s.adresse(), s.parent(), parents);
        }

        @Override
        CentrePayeur corriger(CentrePayeur existant, SaisieDePayeur s, List<Agence> parents) {
            return existant.corriger(s.code(), s.nom(), s.adresse(), s.parent(), parents);
        }

        @Override
        CentrePayeur sousIdentite(CentrePayeur v, UUID id) {
            return CentrePayeur.reconstituer(id, v.centre(), v.code(), v.nom(), v.adresse(), v.agence());
        }

        @Override
        FicheDePayeur fiche(CentrePayeur c, List<Agence> parents) {
            return new FicheDePayeur(c.id(), c.code(), c.nom(), null, c.adresse(), c.agence(), libelleDuParent(c.agence(), parents));
        }
    }

    private Voie<?, ?> voie(Niveau niveau) {
        return switch (niveau) {
            case CAISSE -> caisse;
            case AGENCE -> agence;
            case CENTRE_PAYEUR -> centrePayeur;
        };
    }

    private static NiveauDePayeur niveauDuDomaine(Niveau niveau) {
        return NiveauDePayeur.valueOf(niveau.name());
    }

    private static Anomalie anomalie(int ligne, AnomalieDeSaisie a) {
        return new Anomalie(ligne, a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RefusDePayeur refus(SaisieDePayeurInvalide invalide) {
        return RefusDePayeur.saisieInvalide(invalide.anomalies().stream().map(a -> anomalie(0, a)).toList());
    }

    private static void verifierLUnicite(Payeur payeur, List<? extends Payeur> duCentre) {
        try {
            payeur.verifierLUnicite(duCentre);
        } catch (PayeurDejaExistant existant) {
            throw RefusDePayeur.dejaExistant(existant.getMessage());
        }
    }

    // ---- écritures

    @Override
    public FicheDePayeur creer(UUID centre, Niveau niveau, SaisieDePayeur saisie) {
        return creerDans(voie(niveau), new CentreId(centre), saisie);
    }

    private <P extends Payeur, Q extends Payeur> FicheDePayeur creerDans(Voie<P, Q> voie, CentreId centre, SaisieDePayeur saisie) {
        return unite.executer(() -> {
            List<Q> parents = voie.parents(centre);
            P payeur;
            try {
                payeur = voie.referencer(identifiants.nouveau(), centre, saisie, parents);
            } catch (SaisieDePayeurInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(payeur, voie.payeurs.duCentre(centre));
            voie.payeurs.enregistrer(payeur);
            return voie.fiche(payeur, parents);
        });
    }

    @Override
    public FicheDePayeur modifier(UUID centre, Niveau niveau, UUID id, SaisieDePayeur saisie) {
        return modifierDans(voie(niveau), new CentreId(centre), id, saisie);
    }

    private <P extends Payeur, Q extends Payeur> FicheDePayeur modifierDans(Voie<P, Q> voie, CentreId centre, UUID id,
                                                                            SaisieDePayeur saisie) {
        return unite.executer(() -> {
            P existant = voie.payeurs.trouver(centre, id).orElseThrow(RefusDePayeur::introuvable);
            List<Q> parents = voie.parents(centre);
            P corrige;
            try {
                corrige = voie.corriger(existant, saisie, parents);
            } catch (SaisieDePayeurInvalide invalide) {
                throw refus(invalide);
            }
            verifierLUnicite(corrige, voie.payeurs.duCentre(centre));
            voie.payeurs.remplacer(corrige);
            return voie.fiche(corrige, parents);
        });
    }

    @Override
    public void supprimer(UUID centre, Niveau niveau, UUID id) {
        CentreId centreId = new CentreId(centre);
        Voie<?, ?> voie = voie(niveau);
        unite.executer(() -> {
            voie.payeurs.trouver(centreId, id).orElseThrow(RefusDePayeur::introuvable);
            long references = usage.nombreDeReferences(niveauDuDomaine(niveau), id);
            if (references > 0) throw RefusDePayeur.utilise(references);
            voie.payeurs.supprimer(centreId, id);
            return null;
        });
    }

    // ---- lectures

    @Override
    public PageDePayeurs lister(UUID centre, Niveau niveau, String recherche, int page, int taille) {
        return lecture.page(new CentreId(centre), niveauDuDomaine(niveau), recherche, page, taille);
    }

    @Override
    public List<FicheDePayeur> toutes(UUID centre, Niveau niveau) {
        return toutesDans(voie(niveau), new CentreId(centre));
    }

    private <P extends Payeur, Q extends Payeur> List<FicheDePayeur> toutesDans(Voie<P, Q> voie, CentreId centre) {
        return voie.payeurs.duCentre(centre).stream().map(p -> voie.fiche(p, List.of())).toList();
    }

    // ---- import

    @Override
    public BilanDImport importer(UUID centre, Niveau niveau, List<LigneAImporter> lignes, boolean ecrire) {
        return importerDans(voie(niveau), new CentreId(centre), lignes, ecrire);
    }

    private <P extends Payeur, Q extends Payeur> BilanDImport importerDans(Voie<P, Q> voie, CentreId centre, List<LigneAImporter> lignes,
                                                                          boolean ecrire) {
        return unite.executer(() -> {
            List<P> duCentre = voie.payeurs.duCentre(centre);
            List<Q> parents = voie.parents(centre);
            Map<String, UUID> connus = new HashMap<>();
            for (P payeur : duCentre) connus.putIfAbsent(payeur.cle(), payeur.id());

            Map<String, Integer> vusDansLeFichier = new HashMap<>();
            List<Anomalie> anomalies = new ArrayList<>();
            List<P> aEcrire = new ArrayList<>();
            List<UUID> existants = new ArrayList<>();
            for (LigneAImporter ligne : lignes) {
                P valide;
                try {
                    valide = voie.referencer(SANS_IDENTITE, centre, ligne.saisie(), parents);
                } catch (SaisieDePayeurInvalide invalide) {
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
                UUID connu = existants.get(i);
                if (connu == null) voie.payeurs.enregistrer(voie.sousIdentite(aEcrire.get(i), identifiants.nouveau()));
                else voie.payeurs.remplacer(voie.sousIdentite(aEcrire.get(i), connu));
            }
            return new BilanDImport(aCreer, aMettreAJour, List.of(), true);
        });
    }
}
