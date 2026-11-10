package com.hemodialyse.backend.contexts.patientcouverture.contrat;

import com.hemodialyse.backend.contexts.patientcouverture.application.LectureDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.application.UsageDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agence;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Agences;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CaisseDAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Caisses;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentrePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.CentresPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NiveauDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Payeur;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Payeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.FicheDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.PageDePayeurs;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des trois ports de la chaîne des payeurs, de leur lecture paginée et de leur usage (même jeu de tests de contrat
 * que les tables réelles). Les patients ne sont pas modélisés : leur nombre par centre payeur se règle à la main.
 */
public class PayeursEnMemoire implements LectureDesPayeurs, UsageDesPayeurs {

    private final Map<UUID, CaisseDAssurance> caisses = new LinkedHashMap<>();
    private final Map<UUID, Agence> agences = new LinkedHashMap<>();
    private final Map<UUID, CentrePayeur> centres = new LinkedHashMap<>();
    private final Map<UUID, Long> patients = new HashMap<>();

    private class EnMemoire<P extends Payeur> implements Payeurs<P> {
        private final Map<UUID, P> lignes;

        private EnMemoire(Map<UUID, P> lignes) {
            this.lignes = lignes;
        }

        @Override
        public Optional<P> trouver(CentreId centre, UUID id) {
            return Optional.ofNullable(lignes.get(id)).filter(p -> p.centre().equals(centre));
        }

        @Override
        public List<P> duCentre(CentreId centre) {
            return lignes.values().stream().filter(p -> p.centre().equals(centre)).toList();
        }

        @Override
        public void enregistrer(P payeur) {
            lignes.put(payeur.id(), payeur);
        }

        @Override
        public void remplacer(P payeur) {
            if (trouver(payeur.centre(), payeur.id()).isPresent()) lignes.put(payeur.id(), payeur);
        }

        @Override
        public void supprimer(CentreId centre, UUID id) {
            trouver(centre, id).ifPresent(p -> lignes.remove(id));
        }
    }

    private final class CaissesEnMemoire extends EnMemoire<CaisseDAssurance> implements Caisses {
        CaissesEnMemoire() {
            super(caisses);
        }
    }

    private final class AgencesEnMemoire extends EnMemoire<Agence> implements Agences {
        AgencesEnMemoire() {
            super(agences);
        }
    }

    private final class CentresEnMemoire extends EnMemoire<CentrePayeur> implements CentresPayeurs {
        CentresEnMemoire() {
            super(centres);
        }
    }

    private final Caisses portDesCaisses = new CaissesEnMemoire();
    private final Agences portDesAgences = new AgencesEnMemoire();
    private final CentresPayeurs portDesCentres = new CentresEnMemoire();

    public Caisses caisses() {
        return portDesCaisses;
    }

    public Agences agences() {
        return portDesAgences;
    }

    public CentresPayeurs centresPayeurs() {
        return portDesCentres;
    }

    /** Règle le nombre de patients qui relèvent du centre payeur (les patients ne sont pas modélisés). */
    public void patientsDuCentrePayeur(UUID centrePayeur, long nombre) {
        patients.put(centrePayeur, nombre);
    }

    // ---- usage

    @Override
    public long nombreDeReferences(NiveauDePayeur niveau, UUID id) {
        return switch (niveau) {
            case CAISSE -> agences.values().stream().filter(a -> a.caisse().equals(id)).count();
            case AGENCE -> centres.values().stream().filter(c -> c.agence().equals(id)).count();
            case CENTRE_PAYEUR -> patients.getOrDefault(id, 0L);
        };
    }

    // ---- lecture paginée

    private record Ligne(Payeur payeur, FicheDePayeur fiche, List<String> recherchables) {
    }

    private Optional<Payeur> parent(NiveauDePayeur niveau, Payeur payeur) {
        return switch (niveau) {
            case CAISSE -> Optional.empty();
            case AGENCE -> Optional.ofNullable(caisses.get(((Agence) payeur).caisse()));
            case CENTRE_PAYEUR -> Optional.ofNullable(agences.get(((CentrePayeur) payeur).agence()));
        };
    }

    private Ligne ligne(NiveauDePayeur niveau, Payeur payeur) {
        Optional<Payeur> parent = parent(niveau, payeur);
        String libelleDuParent = parent.map(p -> p.code() + " · " + p.nom()).orElse(null);
        List<String> recherchables = new ArrayList<>(List.of(payeur.code(), payeur.nom()));
        parent.ifPresent(p -> recherchables.addAll(List.of(p.code(), p.nom())));
        FicheDePayeur fiche = switch (niveau) {
            case CAISSE -> {
                CaisseDAssurance c = (CaisseDAssurance) payeur;
                yield new FicheDePayeur(c.id(), c.code(), c.nom(), c.type(), null, null, null);
            }
            case AGENCE -> {
                Agence a = (Agence) payeur;
                yield new FicheDePayeur(a.id(), a.code(), a.nom(), null, null, a.caisse(), libelleDuParent);
            }
            case CENTRE_PAYEUR -> {
                CentrePayeur c = (CentrePayeur) payeur;
                if (c.adresse() != null) recherchables.add(c.adresse());
                yield new FicheDePayeur(c.id(), c.code(), c.nom(), null, c.adresse(), c.agence(), libelleDuParent);
            }
        };
        return new Ligne(payeur, fiche, recherchables);
    }

    @Override
    public PageDePayeurs page(CentreId centre, NiveauDePayeur niveau, String recherche, int page, int taille) {
        List<? extends Payeur> du = switch (niveau) {
            case CAISSE -> portDesCaisses.duCentre(centre);
            case AGENCE -> portDesAgences.duCentre(centre);
            case CENTRE_PAYEUR -> portDesCentres.duCentre(centre);
        };
        String terme = recherche == null || recherche.isBlank() ? null : recherche.trim().toLowerCase(Locale.ROOT);
        List<Ligne> trouvees = new ArrayList<>(du.stream().map(p -> ligne(niveau, p))
                .filter(l -> terme == null || l.recherchables().stream().anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(terme)))
                .sorted(Comparator.comparing((Ligne l) -> l.payeur().code()).thenComparing(l -> l.payeur().id())).toList());
        int debut = (int) Math.min((long) page * taille, trouvees.size());
        List<FicheDePayeur> lignes = trouvees.subList(debut, Math.min(debut + taille, trouvees.size())).stream().map(Ligne::fiche).toList();
        return new PageDePayeurs(lignes, trouvees.size(), page, taille);
    }
}
