package com.hemodialyse.backend.contexts.pilotagedirection.infrastructure.acl.gmao;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursDuCentre;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.SyntheseGmaoDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.application.GmaoDesCentres;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.PatientsAuxGenerateurs;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ACL : le parc d'équipements lu dans {@code gmao.v1} ({@link SyntheseGmaoDuCentre}, {@link GenerateursDuCentre}) et les patients rattachés
 * lus dans le langage publié de la Planification ({@link PatientsAuxGenerateurs}). Traduction pure, aucun SQL.
 * <p>
 * Statuts GMAO d'un générateur « indisponible » : la liste de l'ancienne jointure SQL, reprise telle quelle. Écart assumé (P6) : un patient
 * rattaché au générateur d'un AUTRE centre comptait avant ; il ne compte plus, les générateurs étant lus par centre.
 */
public final class GmaoParLaGmao implements GmaoDesCentres {

    static final Set<String> STATUTS_INDISPONIBLES = Set.of("EN_MAINTENANCE", "EN_ATTENTE_PIECE", "HORS_SERVICE", "A_REFORMER", "REFORME");

    private final SyntheseGmaoDuCentre syntheses;
    private final GenerateursDuCentre generateurs;
    private final PatientsAuxGenerateurs patients;

    public GmaoParLaGmao(SyntheseGmaoDuCentre syntheses, GenerateursDuCentre generateurs, PatientsAuxGenerateurs patients) {
        this.syntheses = syntheses;
        this.generateurs = generateurs;
        this.patients = patients;
    }

    @Override
    public SyntheseGmao duCentre(UUID centre, Instant du, Instant au) {
        SyntheseGmaoDuCentre.Synthese s = syntheses.duCentre(centre, du, au);
        List<EquipementDuParc> equipements = s.equipements().stream()
                .map(e -> new EquipementDuParc(e.id(), e.code(), e.designation(), e.statut(), e.coutDeLaPeriode(), e.coutCumule(),
                        e.prixAcquisition(), e.ratioMaintenance(), e.indisponibiliteHeures(), e.reformeRecommandee()))
                .toList();
        return new SyntheseGmao(s.nombreDEquipements(), s.horsService(), s.enMaintenance(), s.reformes(), s.interventionsEnCours(),
                s.coutDeMaintenance(), s.indisponibiliteHeures(), equipements);
    }

    @Override
    public long patientsSurGenerateurIndisponible(UUID centre) {
        List<UUID> indisponibles = generateurs.duCentre(centre).stream()
                .filter(g -> STATUTS_INDISPONIBLES.contains(g.statut())).map(GenerateursDuCentre.Generateur::id).toList();
        return patients.nombre(centre, indisponibles);
    }
}
