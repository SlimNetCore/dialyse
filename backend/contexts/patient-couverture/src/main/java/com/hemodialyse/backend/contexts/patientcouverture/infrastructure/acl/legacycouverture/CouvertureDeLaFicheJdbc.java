package com.hemodialyse.backend.contexts.patientcouverture.infrastructure.acl.legacycouverture;

import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL du port {@link CouvertureDeLaFiche} vers les tables actuelles ({@code assure}, {@code assure_patient},
 * {@code attestation_droit}, {@code prise_en_charge}) : reproduit à l'identique ce que la saisie de la fiche enregistre, jusqu'à
 * la reprise des slices PC2 (assuré), PC3 (prise en charge) et PC4 (attestation). Retirée avec elles.
 */
public class CouvertureDeLaFicheJdbc implements CouvertureDeLaFiche {

    private final JdbcTemplate jdbc;

    public CouvertureDeLaFicheJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean attestationEnregistree(CentreId centre, PatientId patient) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ?",
                Integer.class, centre.valeur(), patient.valeur());
        return n != null && n > 0;
    }

    @Override
    public Optional<Couverture.AssureSurLaFiche> assurePrincipal(CentreId centre, PatientId patient) {
        return jdbc.queryForList("SELECT numero_assurance FROM assure_patient WHERE center_id = ? AND patient_id = ? "
                        + "AND is_primary = true ORDER BY date_affectation DESC", String.class, centre.valeur(), patient.valeur())
                .stream().findFirst().flatMap(numero -> jdbc.query("SELECT sexe, nom, prenom, date_naissance, tel_personnel, "
                        + "adresse, groupe_sanguin, tel_mobile, tel_bureau FROM assure WHERE numero_assurance = ?", (rs, n) -> {
                    Date naissance = rs.getDate("date_naissance");
                    return new Couverture.AssureSurLaFiche(rs.getString("sexe"), rs.getString("nom"), rs.getString("prenom"),
                            naissance == null ? null : naissance.toLocalDate().toString(), rs.getString("tel_personnel"),
                            rs.getString("adresse"), rs.getString("groupe_sanguin"), rs.getString("tel_mobile"),
                            rs.getString("tel_bureau"));
                }, numero).stream().findFirst());
    }

    @Override
    public void enregistrer(CentreId centre, PatientId patient, Couverture couverture, AttestationSaisie attestation,
                            DemandeDePec demande) {
        assurerLAssurePrincipal(centre, patient, couverture);
        if (attestation != null && attestation.complete()) enregistrerLAttestation(centre, patient, attestation);
        if (demande != null && demande.complete()) enregistrerLaDemande(centre, patient, demande);
    }

    private void assurerLAssurePrincipal(CentreId centre, PatientId patient, Couverture couverture) {
        jdbc.update("UPDATE assure_patient SET is_primary = false WHERE center_id = ? AND patient_id = ?", centre.valeur(),
                patient.valeur());
        if (couverture.assureLuiMeme()) return;
        String numero = couverture.assureNumeroAssurance();
        Couverture.AssureSurLaFiche a = couverture.assure();
        Date naissance = dateOuNulle(a.dateNaissance());
        Object[] champs = {centre.valeur(), a.nom(), a.prenom(), a.sexe(), naissance, a.telPersonnel(), a.telMobile(),
                a.telBureau(), a.adresse(), a.groupeSanguin()};
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM assure WHERE numero_assurance = ?", Integer.class, numero);
        if (existe != null && existe > 0) {
            // une date de naissance absente ou illisible ne modifie rien : l'assuré garde la sienne (comportement actuel)
            jdbc.update("UPDATE assure SET center_id = ?, nom = ?, prenom = ?, sexe = ?, date_naissance = COALESCE(?, date_naissance), "
                    + "tel_personnel = ?, tel_mobile = ?, tel_bureau = ?, adresse = ?, groupe_sanguin = ? "
                    + "WHERE numero_assurance = ?", append(champs, numero));
        } else {
            jdbc.update("INSERT INTO assure (numero_assurance, center_id, nom, prenom, sexe, date_naissance, tel_personnel, "
                    + "tel_mobile, tel_bureau, adresse, groupe_sanguin, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    numero, centre.valeur(), a.nom(), a.prenom(), a.sexe(), naissance, a.telPersonnel(), a.telMobile(),
                    a.telBureau(), a.adresse(), a.groupeSanguin(), OffsetDateTime.now(ZoneOffset.UTC));
        }
        jdbc.update("INSERT INTO assure_patient (id, patient_id, numero_assurance, center_id, is_primary, date_affectation) "
                + "VALUES (?,?,?,?,true,?)", UUID.randomUUID(), patient.valeur(), numero, centre.valeur(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private void enregistrerLAttestation(CentreId centre, PatientId patient, AttestationSaisie a) {
        Integer deja = jdbc.queryForObject("SELECT COUNT(*) FROM attestation_droit WHERE center_id = ? AND patient_id = ? "
                + "AND date_debut = ? AND date_fin = ?", Integer.class, centre.valeur(), patient.valeur(),
                Date.valueOf(a.debut()), Date.valueOf(a.fin()));
        if (deja != null && deja > 0) return;
        if (a.fin().isBefore(a.debut())) throw new SaisieInvalide("Date fin avant date debut");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) "
                + "VALUES (?,?,?,?,?,?)", a.id() != null ? a.id() : UUID.randomUUID(), patient.valeur(), centre.valeur(),
                Date.valueOf(a.debut()), Date.valueOf(a.fin()), OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Enregistrer la fiche ne ramène jamais une prise en charge validée à « créée » : une demande existante (même identifiant,
     * à défaut même demande) est conservée avec son statut ; seule une demande modifiée met la demande à jour.
     */
    private void enregistrerLaDemande(CentreId centre, PatientId patient, DemandeDePec d) {
        record Existante(UUID id, LocalDate debut, LocalDate fin, UUID forfait) {
        }
        var existantes = jdbc.query("SELECT id, date_debut_demande, date_fin_demande, forfait_demande_id FROM prise_en_charge "
                + "WHERE center_id = ? AND patient_id = ?", (rs, n) -> {
            Date debut = rs.getDate("date_debut_demande");
            Date fin = rs.getDate("date_fin_demande");
            return new Existante(rs.getObject("id", UUID.class), debut == null ? null : debut.toLocalDate(),
                    fin == null ? null : fin.toLocalDate(), rs.getObject("forfait_demande_id", UUID.class));
        }, centre.valeur(), patient.valeur());
        Existante trouvee = d.id() == null ? null : existantes.stream().filter(e -> d.id().equals(e.id())).findFirst().orElse(null);
        if (trouvee == null) {
            trouvee = existantes.stream().filter(e -> Objects.equals(e.debut(), d.debut()) && Objects.equals(e.fin(), d.fin())
                    && Objects.equals(e.forfait(), d.forfaitId())).findFirst().orElse(null);
        }
        if (trouvee == null) {
            jdbc.update("INSERT INTO prise_en_charge (id, patient_id, center_id, date_debut_demande, date_fin_demande, "
                            + "forfait_demande_id, statut, created_at) VALUES (?,?,?,?,?,?,'CREE',?)",
                    d.id() != null ? d.id() : UUID.randomUUID(), patient.valeur(), centre.valeur(), Date.valueOf(d.debut()),
                    Date.valueOf(d.fin()), d.forfaitId(), OffsetDateTime.now(ZoneOffset.UTC));
            return;
        }
        if (Objects.equals(trouvee.debut(), d.debut()) && Objects.equals(trouvee.fin(), d.fin())
                && Objects.equals(trouvee.forfait(), d.forfaitId())) {
            return;
        }
        jdbc.update("UPDATE prise_en_charge SET date_debut_demande = ?, date_fin_demande = ?, forfait_demande_id = ? WHERE id = ?",
                Date.valueOf(d.debut()), Date.valueOf(d.fin()), d.forfaitId(), trouvee.id());
    }

    private static Object[] append(Object[] valeurs, Object dernier) {
        Object[] sortie = java.util.Arrays.copyOf(valeurs, valeurs.length + 1);
        sortie[valeurs.length] = dernier;
        return sortie;
    }

    private static Date dateOuNulle(String texte) {
        if (texte == null || texte.isBlank()) return null;
        try {
            return Date.valueOf(LocalDate.parse(texte.trim()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
