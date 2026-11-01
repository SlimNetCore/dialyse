package com.hemodialyse.backend.contexts.comptabilite.interfaces.http.dto;

import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.piece.ModeleDePiece;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ModeleRequete(@NotBlank @Size(max = ModeleDePiece.CODE_MAX) String code,
                            @NotBlank @Size(max = ModeleDePiece.LIBELLE_MAX) String libelle,
                            @NotBlank String journal, boolean actif,
                            @NotEmpty @Size(max = ModeleDePiece.LIGNES_MAX) List<@Valid LigneModeleRequete> lignes) {
    public ModeleDePiece vers(UUID id, CentreId centre) {
        return new ModeleDePiece(id, centre, code, libelle, CodeJournal.de(journal), actif,
                lignes.stream().map(LigneModeleRequete::vers).toList());
    }
}
