package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.FichesDesInfirmiers.CompteLieAUneFicheActive;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.infrastructure.persistence.entity.AppUserJpaEntity;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.persistence.repository.AppUserJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.spec.UserSpecifications;
import com.hemodialyse.backend.infrastructure.web.dto.request.CreateUserRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UpdateUserRequest;
import com.hemodialyse.backend.infrastructure.web.dto.request.UserSearchRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserRestController {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AppUserJpaRepository userRepository;
    //private final LicenseService licenseService;

    /** Fiches d'infirmiers liées aux comptes : référentiel du contexte Planification (langage publié, slice P2). */
    private final FichesDesInfirmiers fichesDesInfirmiers;

    /** Noms des centres : langage publié d'Organisation (IDN-lecture, ADR 0174). */
    private final AnnuaireDesCentres annuaireDesCentres;

    public UserRestController(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AppUserJpaRepository userRepository,
                              FichesDesInfirmiers fichesDesInfirmiers, AnnuaireDesCentres annuaireDesCentres) {
        this.annuaireDesCentres = annuaireDesCentres;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.fichesDesInfirmiers = fichesDesInfirmiers;
        //this.licenseService = licenseService;
    }

    @GetMapping
    public ResponseEntity<?> listUsers(@RequestParam(required = false) UUID centerId) {
        return ResponseEntity.ok(loadUsers(centerId));
    }

    @PostMapping("/search")
    public ResponseEntity<?> searchUsers(@RequestBody @Valid UserSearchRequest req) {
        int page = req.page();
        int size = req.size();
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("username")));
        var result = userRepository.findAll(UserSpecifications.from(req, annuaireDesCentres), pageable);

        Map<UUID, String> noms = nomsDesCentres();
        List<Map<String, Object>> items = result.getContent().stream().map(u -> toUserPayload(u, noms)).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", result.getTotalElements(), "page", page, "size", size));
    }

    private List<Map<String, Object>> loadUsers(UUID centerId) {
        Map<UUID, String> noms = nomsDesCentres();
        if (centerId == null) {
            return userRepository.findAll(Sort.by(Sort.Order.asc("username"))).stream().map(u -> toUserPayload(u, noms)).toList();
        }
        UserSearchRequest request = new UserSearchRequest(centerId, 0, 1000, null, null, null, null, null, null, null);
        return userRepository.findAll(UserSpecifications.from(request, annuaireDesCentres), Sort.by(Sort.Order.asc("username"))).stream()
                .map(u -> toUserPayload(u, noms))
                .toList();
    }

    /** Les noms des centres (langage publié d'Organisation), lus une fois par requête ; un centre sans nom est absent. */
    private Map<UUID, String> nomsDesCentres() {
        Map<UUID, String> noms = new LinkedHashMap<>();
        for (AnnuaireDesCentres.CentreNomme c : annuaireDesCentres.tousAvecNom()) {
            if (c.nom() != null) noms.put(c.id(), c.nom());
        }
        return noms;
    }

    private Map<String, Object> toUserPayload(AppUserJpaEntity user, Map<UUID, String> noms) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("full_name", user.getFullName());
        payload.put("active", user.isActive());
        payload.put("created_at", user.getCreatedAt());
        payload.put("roles", user.getRoles().stream()
                .map(r -> Map.<String, Object>of("id", r.getId(), "code", r.getCode(), "name", r.getName()))
                .toList());
        // Les noms viennent du langage publié d'Organisation (IDN-licence, ADR 0175) ; un rattachement à un centre sans nom est omis.
        List<Map<String, Object>> centres = new ArrayList<>();
        for (UUID centreId : user.getCenterIds()) {
            String nom = noms.get(centreId);
            if (nom != null) centres.add(Map.<String, Object>of("id", centreId, "name", nom));
        }
        payload.put("centers", centres);
        return payload;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable UUID id) {
        var users = jdbc.queryForList(
                "SELECT id AS \"id\", username AS \"username\", email AS \"email\", " +
                        "full_name AS \"full_name\", active AS \"active\", created_at AS \"created_at\" " +
                        "FROM app_user WHERE id = ?", id);
        if (users.isEmpty()) return ResponseEntity.notFound().build();
        var u = users.get(0);
        u.put("roles", jdbc.queryForList(
                "SELECT r.id AS \"id\", r.code AS \"code\", r.name AS \"name\" " +
                        "FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?", id));
        // Les noms des centres viennent du langage publié d'Organisation (IDN-lecture, ADR 0174) ; le rattachement reste à Identité.
        List<Map<String, Object>> centres = new ArrayList<>();
        for (UUID centreId : jdbc.queryForList("SELECT center_id FROM app_user_center WHERE user_id = ?", UUID.class, id)) {
            annuaireDesCentres.nomDuCentre(centreId).ifPresent(nom -> {
                Map<String, Object> centre = new LinkedHashMap<>();
                centre.put("id", centreId);
                centre.put("name", nom);
                centres.add(centre);
            });
        }
        u.put("centers", centres);
        return ResponseEntity.ok(u);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest req, Authentication authentication) {
        // Même verrou que la création d'un compte depuis une fiche d'infirmier : app_user.username n'a aucune unicité en base
        // PostgreSQL (KB-PLN-24) ; le contrôle d'existence qui suit est fait sous ce verrou (tenu jusqu'à la fin de la transaction).
        fichesDesInfirmiers.verrouillerLesIdentifiantsDeConnexion();
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, req.username());
        if (count != null && count > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nom d'utilisateur déjà existant"));
        }

        /*if (req.active() && req.centerIds() != null) {
            for (UUID centerId : req.centerIds()) {
                licenseService.assertSeatAvailable(centerId);
            }
        }*/

        List<UUID> assignableRoleIds = filterAssignableRoleIds(req.roleIds(), authentication);

        UUID id = UUID.randomUUID();
        String hash = passwordEncoder.encode(req.password());
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)",
                id, req.username(), hash, req.email(), req.fullName(), req.active());

        for (UUID roleId : assignableRoleIds) {
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
        }
        if (req.centerIds() != null) {
            for (UUID centerId : req.centerIds()) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
            }
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody UpdateUserRequest req, Authentication authentication) {
        // Ordre global des verrous (ADR 0056) : fiches d'infirmier AVANT la ligne app_user. La fiche reliée à ce compte, dans le
        // centre de l'administrateur, suit son état (actif / inactif) ; elle est donc verrouillée et écrite en premier.
        fichesDesInfirmiers.suivreLEtatDuCompte(id, req.active(), centreDeLAdministrateur(authentication));
        if (req.password() != null && !req.password().isBlank()) {
            String hash = passwordEncoder.encode(req.password());
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=?, password_hash=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), hash, id);
        } else {
            jdbc.update("UPDATE app_user SET email=?, full_name=?, active=? WHERE id=?",
                    req.email(), req.fullName(), req.active(), id);
        }

        if (req.roleIds() != null) {
            List<UUID> assignableRoleIds = filterAssignableRoleIds(req.roleIds(), authentication);
            // A non-SUPERADMIN caller must not be able to strip an existing SUPERADMIN
            // grant from a target user either — preserve it rather than silently drop it.
            boolean callerIsSuperAdmin = hasAuthority(authentication, "ROLE_SUPERADMIN");
            if (!callerIsSuperAdmin) {
                List<UUID> existingSuperAdminRoleIds = jdbc.queryForList(
                        "SELECT ur.role_id FROM app_user_role ur INNER JOIN app_role r ON r.id = ur.role_id " +
                                "WHERE ur.user_id = ? AND r.code = 'SUPERADMIN'", UUID.class, id);
                assignableRoleIds.addAll(existingSuperAdminRoleIds);
            }
            jdbc.update("DELETE FROM app_user_role WHERE user_id = ?", id);
            for (UUID roleId : assignableRoleIds) {
                jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
            }
        }
        if (req.centerIds() != null) {
            jdbc.update("DELETE FROM app_user_center WHERE user_id = ?", id);
            for (UUID centerId : req.centerIds()) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
            }
        }
        return ResponseEntity.ok(Map.of("id", id));
    }

    /**
     * Un compte lié à une fiche d'infirmier active ne se supprime pas (422) ; sinon ses fiches inactives sont déliées et le
     * compte supprimé dans la même transaction.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        try {
            fichesDesInfirmiers.delierAvantLaSuppressionDuCompte(id);
        } catch (CompteLieAUneFicheActive e) {
            throw new BusinessException("COMPTE_LIE_A_UNE_FICHE_ACTIVE", e.getMessage());
        }
        jdbc.update("DELETE FROM app_user WHERE id = ?", id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /** Centre de la session de l'administrateur, ou {@code null} (accès transverse sans centre). */
    private UUID centreDeLAdministrateur(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.getCenterId() != null && !principal.getCenterId().isBlank()) {
            return UUID.fromString(principal.getCenterId());
        }
        return null;
    }

    /**
     * SUPERADMIN is reserved for the vendor's own account and must never be
     * grantable through the regular user-management screen — otherwise any center
     * ADMIN could self-elevate. Strips it out of the requested role list unless the
     * caller already holds it.
     */
    private List<UUID> filterAssignableRoleIds(List<UUID> requestedRoleIds, Authentication authentication) {
        List<UUID> result = new ArrayList<>();
        if (requestedRoleIds == null) {
            return result;
        }
        boolean callerIsSuperAdmin = hasAuthority(authentication, "ROLE_SUPERADMIN");
        for (UUID roleId : requestedRoleIds) {
            if (!callerIsSuperAdmin && "SUPERADMIN".equals(jdbc.queryForObject(
                    "SELECT code FROM app_role WHERE id = ?", String.class, roleId))) {
                continue;
            }
            result.add(roleId);
        }
        return result;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}

