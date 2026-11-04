package com.hemodialyse.backend.characterization.patientcouverture.pc4;

import com.hemodialyse.backend.characterization.patientcouverture.CouvertureCharacterizationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Harnais de la slice PC4 (attestations de droits), au-dessus du harnais PC2/PC3 (décor, dates nommées, diffusions, audit, garde de
 * minuit, onze profils).
 * <p>
 * Point d'observation : HTTP sur les quatre routes d'attestation de {@code PecRestController} ({@code /api/v1/pec/attestations},
 * {@code /attestations/{patientId}}, {@code /attestations/{id}} en suppression, {@code /attestations-center/search}), état SQL de
 * {@code attestation_droit}, notifications, diffusions, audit ; les lecteurs (éligibilité de la séance, route {@code session-allowed},
 * tableau de bord) sont appelés tels que câblés. Aucun mock.
 */
public abstract class AttestationCharacterizationSupport extends CouvertureCharacterizationSupport {

    protected static final String ATTESTATIONS = PEC + "/attestations";

    @Override
    protected String racine() {
        return "../patientcouverture/pc4/";
    }

    /** L'adaptateur d'attestation met en cache la liste par patient : une écriture directe en base ne doit pas fuir d'un test à l'autre. */
    @BeforeEach
    protected void viderLesCaches() {
        caches.getCacheNames().forEach(n -> java.util.Optional.ofNullable(caches.getCache(n)).ifPresent(org.springframework.cache.Cache::clear));
    }

    /** Centre inexistant visé par les scénarios de rattachement (aucune ligne ne doit survivre à un test). */
    protected static final UUID CENTRE_INCONNU = UUID.nameUUIDFromBytes("pc4-centre-inconnu".getBytes());

    @Override
    protected void nettoyerCouverture() {
        super.nettoyerCouverture();
        jdbc.update("DELETE FROM attestation_droit WHERE center_id = ?", CENTRE_INCONNU);
        for (UUID centre : java.util.List.of(CENTRE_A, CENTRE_B)) jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
    }

    /** {@code POST /api/v1/pec/attestations} avec un corps minimal ({@code null} = champ présent à null). */
    protected Resp creerAttestation(UUID patient, UUID centre, LocalDate debut, LocalDate fin, RequestPostProcessor who) throws Exception {
        return doPost(ATTESTATIONS, corps(champs("patientId", patient, "centerId", centre, "dateDebut", debut == null ? null : debut.toString(),
                "dateFin", fin == null ? null : fin.toString())), who);
    }

    protected Resp attestationsDuPatient(UUID patient, UUID centre, RequestPostProcessor who) throws Exception {
        return doGet(ATTESTATIONS + "/" + patient + "?centerId=" + centre, who);
    }

    protected Resp supprimerAttestation(UUID attestation, UUID centre, UUID patient, RequestPostProcessor who) throws Exception {
        return doDelete(ATTESTATIONS + "/" + attestation + "?centerId=" + centre + (patient == null ? "" : "&patientId=" + patient), who);
    }

    protected Resp rechercher(String corpsJson, RequestPostProcessor who) throws Exception {
        return doPost(ATTESTATIONS + "-center/search", json(corpsJson), who);
    }

    /** Attestation posée en base, nommée {@code <alias>} ; rend son identifiant. */
    protected UUID attestationEnBase(UUID centre, String alias, UUID patient, LocalDate debut, LocalDate fin) {
        UUID id = idStable("attestation", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO attestation_droit (id, patient_id, center_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)", id,
                patient, centre, sqlDate(debut), sqlDate(fin), java.time.OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, java.time.ZoneOffset.UTC));
        return id;
    }

    protected void etatDesAttestations(String name) {
        sql(name, "SELECT p.nom AS patient, a.center_id, a.date_debut, a.date_fin FROM attestation_droit a LEFT JOIN patients p ON p.id = a.patient_id "
                + "WHERE a.center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY p.nom, a.center_id, a.date_debut, a.date_fin");
    }

    protected int nombreDAttestations() {
        return nombreDe("attestation_droit");
    }

    protected String empreinteDesAttestations() {
        return jdbc.queryForList("SELECT * FROM attestation_droit WHERE center_id IN (?, ?) ORDER BY id", CENTRE_A, CENTRE_B).toString();
    }
}
