package com.hemodialyse.backend.contexts.documents.interfaces.http;

import com.hemodialyse.backend.contexts.documents.application.ListerLesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** La liste des types de document : même URL, mêmes champs ({@code code}, {@code label}) ; un verbe non géré est un 405. */
class TypesControleurTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TypesControleur(new ListerLesTypesDeDocument(List.of(
            () -> List.of(new TypeDeDocumentDeclare("PEC", "Prise en charge", "reports/prise_en_charge.jrxml", List.of(), 1)))))).build();

    @Test
    void should_list_the_declared_types_then_the_custom_report() throws Exception {
        mvc.perform(get("/api/v1/documents/types")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("PEC")).andExpect(jsonPath("$[0].label").value("Prise en charge"))
                .andExpect(jsonPath("$[1].code").value("CUSTOM")).andExpect(jsonPath("$[1].label").value("Rapport personnalisé"))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void should_answer_405_with_the_allowed_verbs_to_a_write_verb() throws Exception {
        mvc.perform(post("/api/v1/documents/types")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET, HEAD, OPTIONS")).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(delete("/api/v1/documents/types")).andExpect(status().isMethodNotAllowed());
    }
}
