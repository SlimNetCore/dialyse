package com.hemodialyse.backend.application.infirmier;

import com.hemodialyse.backend.application.infirmier.InfirmierService.InfirmierDetail;
import com.hemodialyse.backend.domain.infirmier.model.Infirmier;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort;
import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort.CompteRef;
import com.hemodialyse.backend.domain.infirmier.port.InfirmierRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Relation entre une fiche infirmier et un compte utilisateur : liaison d'un compte existant, création d'un compte
 * depuis la fiche, déliaison, et synchronisation de l'état (actif / supprimé) du compte vers la fiche.
 */
@Service
public class CompteInfirmierService {

    private static final Pattern IDENTIFIANT = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    private final InfirmierRepositoryPort infirmiers;
    private final ComptesInfirmierPort comptes;
    private final InfirmierService referentiel;
    private final GenerateurMotDePasseTemporaire motsDePasse;

    public CompteInfirmierService(InfirmierRepositoryPort infirmiers, ComptesInfirmierPort comptes,
                                  InfirmierService referentiel, GenerateurMotDePasseTemporaire motsDePasse) {
        this.infirmiers = infirmiers;
        this.comptes = comptes;
        this.referentiel = referentiel;
        this.motsDePasse = motsDePasse;
    }

    public PagedResult<CompteRef> comptesLiables(UUID centerId, int page, int size) {
        return comptes.comptesLiables(centerId, page, size);
    }

    public InfirmierDetail lier(UUID centerId, UUID infirmierId, UUID userId) {
        Infirmier fiche = ficheSansCompte(centerId, infirmierId);
        CompteRef compte = comptes.trouverCompteInfirmier(centerId, userId)
                .orElseThrow(() -> new BusinessException("COMPTE_INTROUVABLE",
                        "Ce compte n'existe pas dans le centre ou n'a pas le rôle infirmier"));
        if (infirmiers.findByUserId(centerId, userId).isPresent()) {
            throw new BusinessException("COMPTE_DEJA_UTILISE", "Ce compte est déjà relié à un autre infirmier");
        }
        if (compte.actif() != fiche.actif()) {
            throw new BusinessException("COMPTE_ETAT_INCOHERENT",
                    "Le compte et la fiche doivent être tous deux actifs ou tous deux inactifs");
        }
        return referentiel.detail(centerId, infirmiers.save(fiche.lierCompte(userId)));
    }

    public CompteCree creerEtLier(UUID centerId, UUID infirmierId, String identifiant, String email) {
        Infirmier fiche = ficheSansCompte(centerId, infirmierId);
        if (!fiche.actif()) {
            throw new BusinessException("COMPTE_ETAT_INCOHERENT", "Impossible de créer un compte pour une fiche inactive");
        }
        String username = identifiant == null ? "" : identifiant.trim();
        if (!IDENTIFIANT.matcher(username).matches()) {
            throw new BusinessException("COMPTE_IDENTIFIANT_INVALIDE",
                    "Identifiant invalide (3 à 50 caractères : lettres, chiffres, point, tiret, souligné)");
        }
        if (comptes.identifiantExiste(username)) {
            throw new BusinessException("COMPTE_IDENTIFIANT_EXISTANT", "Cet identifiant de connexion existe déjà");
        }
        String motDePasse = motsDePasse.generer();
        CompteRef compte = comptes.creerCompteInfirmier(centerId, username, motDePasse, fiche.nomComplet(),
                email == null || email.isBlank() ? null : email.trim());
        return new CompteCree(referentiel.detail(centerId, infirmiers.save(fiche.lierCompte(compte.id()))), motDePasse);
    }

    public InfirmierDetail delier(UUID centerId, UUID infirmierId) {
        Infirmier fiche = infirmiers.findById(centerId, infirmierId)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
        return referentiel.detail(centerId, infirmiers.save(fiche.delierCompte()));
    }

    /**
     * Le compte a été activé ou désactivé dans l'administration des utilisateurs : les fiches reliées suivent.
     */
    public void surChangementEtatCompte(UUID userId, boolean actif) {
        for (Infirmier fiche : infirmiers.findAllByUserId(userId)) {
            if (fiche.actif() != actif) infirmiers.save(actif ? fiche.reactiver() : fiche.desactiver());
        }
    }

    /**
     * Le compte a été supprimé : les fiches reliées sont conservées mais n'ont plus de compte.
     */
    public void surSuppressionCompte(UUID userId) {
        for (Infirmier fiche : infirmiers.findAllByUserId(userId)) infirmiers.save(fiche.delierCompte());
    }

    private Infirmier ficheSansCompte(UUID centerId, UUID infirmierId) {
        Infirmier fiche = infirmiers.findById(centerId, infirmierId)
                .orElseThrow(() -> new BusinessException("INFIRMIER_INTROUVABLE", "Infirmier introuvable"));
        if (fiche.userId() != null) {
            throw new BusinessException("COMPTE_DEJA_LIE", "Cette fiche est déjà reliée à un compte");
        }
        return fiche;
    }

    /**
     * Fiche reliée à un compte nouvellement créé et mot de passe temporaire (communiqué une seule fois).
     */
    public record CompteCree(InfirmierDetail infirmier, String motDePasseTemporaire) {
    }
}
