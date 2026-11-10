package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import com.hemodialyse.backend.contexts.seance.infrastructure.persistence.VoletsDeLaSeanceJdbc;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L8 (revue L3-L4-L7, constat M2, décision B) : la LECTURE du détail cherche le volet paramédical par l'identifiant de séance, comme la SAISIE
 * (KB-SEA-94). La séance est déjà bornée au centre en amont ; l'utilisateur revoit ce qu'il vient d'enregistrer, même sous un volet posé sous
 * un autre centre (donnée à réparer par §78, jamais réécrite automatiquement).
 */
class VoletsDeLaSeanceLuParIdDeSeanceTest {

    private static final CentreId A = new CentreId(new UUID(0, 0xA));
    private static final CentreId B = new CentreId(new UUID(0, 0xB));
    private static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));

    @Test
    void le_detail_montre_le_volet_pose_sous_un_autre_centre_que_celui_de_la_seance_comme_la_saisie_le_retrouve() {
        var paramedicaux = new VoletsParamedicauxEnMemoire();
        paramedicaux.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), B, SEANCE, VoletsParamedicauxContrat.completes(),
                Instant.parse("2026-06-15T10:00:00Z")));
        var lecture = new VoletsDeLaSeanceJdbc(paramedicaux, new VoletsMedicauxEnMemoire());

        var volet = lecture.paramedical(A, SEANCE);

        assertThat(volet).isPresent();
        assertThat(volet.get().tensionAvant()).isEqualTo("130/80");
    }

    @Test
    void le_detail_montre_le_volet_pose_sous_le_centre_de_la_seance() {
        var paramedicaux = new VoletsParamedicauxEnMemoire();
        paramedicaux.enregistrer(VoletParamedical.ouvrir(new UUID(8, 1), A, SEANCE, VoletsParamedicauxContrat.completes(),
                Instant.parse("2026-06-15T10:00:00Z")));

        assertThat(new VoletsDeLaSeanceJdbc(paramedicaux, new VoletsMedicauxEnMemoire()).paramedical(A, SEANCE)).isPresent();
    }
}
