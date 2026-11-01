package com.hemodialyse.backend.contexts.absencespatients.interfaces.http.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RattrapageReponse(int creees, int annulees, LocalDate fin, Boolean finRamenee, Integer echecs,
                                List<LocalDate> joursIgnores) {
}
