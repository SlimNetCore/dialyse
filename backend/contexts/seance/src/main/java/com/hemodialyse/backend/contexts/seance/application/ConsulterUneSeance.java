package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consulte une séance du centre de la session (GET /api/v1/seances/{id}) : la séance, son patient, ses volets, ses consommables valorisés
 * et son forfait courant. Lecture seule, garde de centre (403 avant tout 400). Séance inconnue ou d'un autre centre : refus. Un patient
 * disparu refuse en 400 « Patient introuvable » comme le legacy (KB-SEA-07 reproduit). Les droits sont portés par l'interface.
 */
public class ConsulterUneSeance {

    private final SessionCourante session;
    private final Seances seances;
    private final PatientsDeLaSeance patients;
    private final GenerateursDesPatients generateurs;
    private final VoletsDeLaSeance volets;
    private final ConsommationsDeLaSeance consommations;
    private final ForfaitsDeSeance forfaits;

    public ConsulterUneSeance(SessionCourante session, Seances seances, PatientsDeLaSeance patients,
                              GenerateursDesPatients generateurs, VoletsDeLaSeance volets,
                              ConsommationsDeLaSeance consommations, ForfaitsDeSeance forfaits) {
        this.session = session;
        this.seances = seances;
        this.patients = patients;
        this.generateurs = generateurs;
        this.volets = volets;
        this.consommations = consommations;
        this.forfaits = forfaits;
    }

    public DetailDeSeance executer(UUID centreDemande, UUID seanceDemandee) {
        var centre = session.centre(centreDemande);
        SeanceId id = new SeanceId(seanceDemandee);
        Seance seance = seances.trouver(centre, id).orElseThrow(SeanceIntrouvable::new);
        FicheDuPatient fiche = patients.fiche(centre, seance.patient()).orElseThrow(() -> new SaisieInvalide("Patient introuvable"));
        fiche = Generateurs.preciser(generateurs, centre, fiche);
        List<ConsommationsDeLaSeance.Ligne> lignes = consommations.historique(centre, id);
        BigDecimal total = lignes.stream()
                .map(l -> l.totalValorise() == null ? BigDecimal.ZERO : l.totalValorise())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new DetailDeSeance(seance, fiche, volets.paramedical(centre, id), volets.medical(centre, id), lignes, total,
                forfaits.courant(centre, id, seance.patient(), seance.jour()));
    }

    public record DetailDeSeance(Seance seance, FicheDuPatient patient,
                                 Optional<VoletsDeLaSeance.VoletParamedical> paramedical,
                                 Optional<VoletsDeLaSeance.VoletMedical> medical,
                                 List<ConsommationsDeLaSeance.Ligne> consommables, BigDecimal totalValoriseDesConsommables,
                                 Optional<ForfaitsDeSeance.ForfaitCourant> forfait) {
    }
}
