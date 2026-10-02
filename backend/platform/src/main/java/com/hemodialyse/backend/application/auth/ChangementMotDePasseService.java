package com.hemodialyse.backend.application.auth;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Changement de mot de passe par l'utilisateur connecté, obligatoire à la première connexion d'un compte créé avec un
 * mot de passe temporaire. L'indicateur est lu à chaque requête par le filtre qui bloque l'API tant que le mot de
 * passe n'a pas été remplacé : il est mis en cache une minute (clé : utilisateur — donnée propre à l'utilisateur, pas à
 * un centre) et vidé dès que le mot de passe change.
 */
@Service
public class ChangementMotDePasseService {

    public static final String CACHE_DOIT_CHANGER = "auth.mustChangePassword";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public ChangementMotDePasseService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Cacheable(cacheNames = CACHE_DOIT_CHANGER, key = "#userId")
    public boolean doitChanger(UUID userId) {
        return jdbc.query("SELECT must_change_password FROM app_user WHERE id = ?",
                (rs, i) -> rs.getBoolean(1), userId).stream().findFirst().orElse(false);
    }

    /**
     * @throws BusinessException mot de passe actuel erroné, nouveau mot de passe identique ou trop faible
     */
    @CacheEvict(cacheNames = CACHE_DOIT_CHANGER, key = "#userId")
    public void changer(UUID userId, String motDePasseActuel, String nouveauMotDePasse) {
        var compte = jdbc.query("SELECT username, password_hash FROM app_user WHERE id = ? AND active = TRUE",
                        (rs, i) -> new String[]{rs.getString(1), rs.getString(2)}, userId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PASSWORD_COMPTE_INTROUVABLE", "Compte introuvable ou inactif"));
        if (motDePasseActuel == null || !passwordEncoder.matches(motDePasseActuel, compte[1])) {
            throw new BusinessException("PASSWORD_ACTUEL_INVALIDE", "Le mot de passe actuel est incorrect");
        }
        if (motDePasseActuel.equals(nouveauMotDePasse)) {
            throw new BusinessException("PASSWORD_IDENTIQUE", "Le nouveau mot de passe doit être différent de l'actuel");
        }
        PolitiqueMotDePasse.refus(nouveauMotDePasse, compte[0]).ifPresent(code -> {
            throw new BusinessException(code, "Mot de passe trop faible : "
                    + PolitiqueMotDePasse.LONGUEUR_MIN + " caractères minimum, avec une lettre et un chiffre");
        });
        jdbc.update("UPDATE app_user SET password_hash = ?, must_change_password = FALSE WHERE id = ?",
                passwordEncoder.encode(nouveauMotDePasse), userId);
    }
}
