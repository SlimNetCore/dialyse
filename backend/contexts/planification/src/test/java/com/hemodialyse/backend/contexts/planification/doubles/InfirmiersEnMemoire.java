package com.hemodialyse.backend.contexts.planification.doubles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.application.GenerateurDeMotDePasse;
import com.hemodialyse.backend.contexts.planification.application.NouveauxIdentifiantsDInfirmier;
import com.hemodialyse.backend.contexts.planification.application.RoulementsDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.InfirmierId;
import com.hemodialyse.backend.contexts.planification.domain.Infirmier;
import com.hemodialyse.backend.contexts.planification.domain.InfirmiersRepository;
import com.hemodialyse.backend.contexts.planification.domain.Matricule;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.VerrouDuReferentielDesInfirmiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Double en mémoire du référentiel des infirmiers : un seul « monde » partagé par les fiches, les comptes (Identité et accès) et le
 * roulement (P3), comme la base partage ses tables. Chaque double passe le même contrat que l'adaptateur JDBC.
 */
public final class InfirmiersEnMemoire {

    public static final class CompteEnMemoire {
        public final CompteId id;
        public final String identifiant;
        public String nomComplet;
        public boolean actif;
        public final Set<CentreId> centres = new HashSet<>();
        public boolean roleInfirmier = true;
        public String email;
        public String motDePasse;

        CompteEnMemoire(CompteId id, String identifiant, String nomComplet, boolean actif) {
            this.id = id;
            this.identifiant = identifiant;
            this.nomComplet = nomComplet;
            this.actif = actif;
        }
    }

    private final Map<InfirmierId, Infirmier> fiches = new LinkedHashMap<>();
    public final Map<CompteId, CompteEnMemoire> comptes = new LinkedHashMap<>();
    public final Map<InfirmierId, List<RoulementsDesInfirmiers.AffectationLue>> roulements = new HashMap<>();
    public final List<CentreId> verrous = new ArrayList<>();
    /**
     * Les fiches lues sous verrou (P3 : le verrou de la fiche de l'infirmier), dans l'ordre.
     */
    public final List<InfirmierId> fichesVerrouillees = new ArrayList<>();
    public int transactions;
    public int verrousDIdentifiants;
    public int prochainIdentifiant = 1;
    public String motDePasseGenere = "Mot2passe!Temp";

    /** Pose un compte INFIRMIER rattaché aux centres donnés. */
    public CompteId compte(String identifiant, String nomComplet, boolean actif, CentreId... centres) {
        CompteEnMemoire compte = new CompteEnMemoire(new CompteId(UUID.randomUUID()), identifiant, nomComplet, actif);
        compte.centres.addAll(List.of(centres));
        comptes.put(compte.id, compte);
        return compte.id;
    }

    public final InfirmiersRepository infirmiers = new InfirmiersRepository() {
        @Override
        public Optional<Infirmier> pourEcriture(CentreId centre, InfirmierId id) {
            fichesVerrouillees.add(id);
            return Optional.ofNullable(fiches.get(id)).filter(f -> f.centre().equals(centre));
        }

        @Override
        public Optional<Infirmier> parId(CentreId centre, InfirmierId id) {
            return Optional.ofNullable(fiches.get(id)).filter(f -> f.centre().equals(centre));
        }

        @Override
        public PageDeResultats<Infirmier> page(CentreId centre, Pagination pagination) {
            List<Infirmier> toutes = fiches.values().stream().filter(f -> f.centre().equals(centre))
                    .sorted(Comparator.comparing((Infirmier f) -> f.identite().nom())
                            .thenComparing(f -> f.identite().prenom() == null ? "" : f.identite().prenom())
                            .thenComparing(f -> f.matricule().valeur())).toList();
            int debut = (int) Math.min(pagination.decalage(), toutes.size());
            int fin = (int) Math.min(debut + (long) pagination.taille(), toutes.size());
            return new PageDeResultats<>(toutes.subList(debut, fin), toutes.size(), pagination.page(), pagination.taille());
        }

        @Override
        public Optional<Infirmier> parMatricule(CentreId centre, Matricule matricule) {
            return fiches.values().stream().filter(f -> f.centre().equals(centre) && f.matricule().memeQue(matricule)).findFirst();
        }

        @Override
        public Optional<Infirmier> parCompte(CentreId centre, CompteId compte) {
            return fiches.values().stream().filter(f -> f.centre().equals(centre) && f.compte().filter(compte::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<Infirmier> fichesDuCompteDuCentre(CentreId centre, CompteId compte) {
            return fiches.values().stream().filter(f -> f.centre().equals(centre) && f.compte().filter(compte::equals).isPresent())
                    .toList();
        }

        @Override
        public List<Infirmier> fichesDuCompte(CompteId compte) {
            return fiches.values().stream().filter(f -> f.compte().filter(compte::equals).isPresent()).toList();
        }

        @Override
        public void enregistrer(Infirmier infirmier) {
            fiches.put(infirmier.id(), infirmier);
        }
    };

    public final ComptesUtilisateurs comptesUtilisateurs = new ComptesUtilisateurs() {
        private ComptesUtilisateurs.Compte vue(CompteEnMemoire c) {
            return new ComptesUtilisateurs.Compte(c.id, c.identifiant, c.nomComplet, c.actif);
        }

        @Override
        public Optional<ComptesUtilisateurs.Compte> compteInfirmierDuCentre(CentreId centre, CompteId compte) {
            return Optional.ofNullable(comptes.get(compte)).filter(c -> c.roleInfirmier && c.centres.contains(centre)).map(this::vue);
        }

        @Override
        public List<ComptesUtilisateurs.Compte> parIds(Collection<CompteId> ids) {
            return ids.stream().map(comptes::get).filter(c -> c != null).map(this::vue).toList();
        }

        @Override
        public PageDeResultats<ComptesUtilisateurs.Compte> comptesLiables(CentreId centre, Pagination pagination) {
            List<CompteEnMemoire> libres = comptes.values().stream().filter(c -> c.roleInfirmier && c.centres.contains(centre))
                    .filter(c -> infirmiers.parCompte(centre, c.id).isEmpty())
                    .sorted(Comparator.comparing((CompteEnMemoire c) -> c.identifiant)).toList();
            int debut = (int) Math.min(pagination.decalage(), libres.size());
            int fin = (int) Math.min(debut + (long) pagination.taille(), libres.size());
            return new PageDeResultats<>(libres.subList(debut, fin).stream().map(this::vue).toList(), libres.size(),
                    pagination.page(), pagination.taille());
        }

        @Override
        public boolean identifiantExiste(IdentifiantDeConnexion identifiant) {
            return comptes.values().stream().anyMatch(c -> c.identifiant.equalsIgnoreCase(identifiant.valeur()));
        }

        @Override
        public ComptesUtilisateurs.Compte creerCompteInfirmier(CentreId centre, IdentifiantDeConnexion identifiant,
                                                               String motDePasseTemporaire, String nomComplet, AdresseEmail email) {
            CompteId id = compte(identifiant.valeur(), nomComplet, true, centre);
            comptes.get(id).email = email == null ? null : email.valeur();
            comptes.get(id).motDePasse = motDePasseTemporaire;
            return vue(comptes.get(id));
        }

        @Override
        public void verrouillerLesIdentifiants() {
            verrousDIdentifiants++;
        }

        @Override
        public void definirActif(CompteId compte, boolean actif) {
            CompteEnMemoire c = comptes.get(compte);
            if (c != null) {
                c.actif = actif;
            }
        }
    };

    public final RoulementsDesInfirmiers roulementsDesInfirmiers = (centre, ids) -> {
        Map<InfirmierId, List<RoulementsDesInfirmiers.AffectationLue>> resultat = new HashMap<>();
        ids.forEach(id -> {
            if (roulements.containsKey(id)) {
                resultat.put(id, roulements.get(id));
            }
        });
        return resultat;
    };

    public final VerrouDuReferentielDesInfirmiers verrou = verrous::add;

    public final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            transactions++;
            return action.get();
        }
    };

    public final GenerateurDeMotDePasse motsDePasse = () -> motDePasseGenere;

    public final NouveauxIdentifiantsDInfirmier identifiants = () -> new InfirmierId(new UUID(0, prochainIdentifiant++));
}
