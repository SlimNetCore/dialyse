package com.hemodialyse.backend.infrastructure.web.rest.gmao;

import com.hemodialyse.backend.domain.gmao.model.DocumentIntervention;
import com.hemodialyse.backend.domain.gmao.model.Intervention;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeDocumentIntervention;
import com.hemodialyse.backend.domain.gmao.model.TypeIntervention;
import com.hemodialyse.backend.domain.gmao.port.DocumentInterventionRepositoryPort;
import com.hemodialyse.backend.domain.gmao.port.InterventionRepositoryPort;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import com.hemodialyse.backend.infrastructure.web.dto.response.gmao.DocumentInterventionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InterventionDocumentRestControllerTest {

    private static final byte[] PDF = "%PDF-1.7 facture".getBytes();

    private final InterventionRepositoryPort interventions = mock(InterventionRepositoryPort.class);
    private final DocumentInterventionRepositoryPort documents = mock(DocumentInterventionRepositoryPort.class);
    private final InterventionDocumentRestController controller =
            new InterventionDocumentRestController(interventions, documents);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ajouter_should_store_a_validated_document_for_an_intervention_of_the_center() throws IOException {
        UUID centreId = authenticate();
        Intervention intervention = intervention(centreId);
        when(interventions.findById(intervention.getId())).thenReturn(Optional.of(intervention));

        ResponseEntity<DocumentInterventionResponse> response = controller.ajouter(
                intervention.getId(), new MockMultipartFile("file", "facture.pdf", "text/plain", PDF), "FACTURE", auth());

        assertEquals(201, response.getStatusCode().value());
        assertEquals("application/pdf", response.getBody().contentType());
        assertEquals(TypeDocumentIntervention.FACTURE, response.getBody().type());
        verify(documents).save(any());
    }

    @Test
    void ajouter_should_refuse_a_disguised_file() {
        UUID centreId = authenticate();
        Intervention intervention = intervention(centreId);
        when(interventions.findById(intervention.getId())).thenReturn(Optional.of(intervention));

        assertThrows(IllegalArgumentException.class, () -> controller.ajouter(
                intervention.getId(),
                new MockMultipartFile("file", "facture.pdf", "application/pdf", "<script>x</script>".getBytes()),
                "FACTURE", auth()));
        verify(documents, never()).save(any());
    }

    @Test
    void every_action_should_be_denied_for_an_intervention_of_another_center() {
        authenticate();
        Intervention autreCentre = intervention(UUID.randomUUID());
        when(interventions.findById(autreCentre.getId())).thenReturn(Optional.of(autreCentre));
        UUID id = autreCentre.getId();
        UUID docId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> controller.lister(id, 0, 20, auth()));
        assertThrows(IllegalArgumentException.class, () -> controller.telecharger(id, docId, auth()));
        assertThrows(IllegalArgumentException.class, () -> controller.supprimer(id, docId, auth()));
        assertThrows(IllegalArgumentException.class, () -> controller.ajouter(
                id, new MockMultipartFile("file", "f.pdf", "application/pdf", PDF), "AUTRE", auth()));
        verifyNoInteractions(documents);
    }

    @Test
    void telecharger_should_serve_an_attachment_and_refuse_a_document_of_another_intervention() {
        UUID centreId = authenticate();
        Intervention intervention = intervention(centreId);
        when(interventions.findById(intervention.getId())).thenReturn(Optional.of(intervention));
        DocumentIntervention doc = DocumentIntervention.creer(intervention.getId(), centreId,
                TypeDocumentIntervention.BON_INTERVENTION, "bon.pdf", PDF, UUID.randomUUID());
        DocumentIntervention foreign = DocumentIntervention.creer(UUID.randomUUID(), centreId,
                TypeDocumentIntervention.BON_INTERVENTION, "autre.pdf", PDF, UUID.randomUUID());
        when(documents.findWithContenuById(doc.id())).thenReturn(Optional.of(doc));
        when(documents.findWithContenuById(foreign.id())).thenReturn(Optional.of(foreign));

        ResponseEntity<byte[]> response = controller.telecharger(intervention.getId(), doc.id(), auth());

        assertEquals("application/pdf", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").startsWith("attachment"));
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertThrows(IllegalArgumentException.class,
                () -> controller.telecharger(intervention.getId(), foreign.id(), auth()));
    }

    private Intervention intervention(UUID centreId) {
        return Intervention.creer(UUID.randomUUID(), centreId, TypeIntervention.CURATIVE,
                OffsetDateTime.now(ZoneOffset.UTC), "Panne", null, StatutEquipement.EN_MAINTENANCE, UUID.randomUUID());
    }

    private UUID authenticate() {
        UUID centerId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(
                UUID.randomUUID().toString(), centerId.toString(), "admin", "", List.of("ADMIN"), true);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return centerId;
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
