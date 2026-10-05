package com.hemodialyse.backend.domain.seance.vo;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Value Object — arterial blood pressure ("systolic/diastolic" in mmHg).
 * <p>
 * Immutable; encapsulates the parsing/validation previously scattered as raw
 * {@code String} fields on the (anemic) {@code VoletParamedical} sheet
 * (primitive obsession — AGENTS.md §14). Physiologically bounded to avoid
 * obvious input errors.
 */
public final class TensionArterielle {

    private static final int MIN_MMHG = 20;
    private static final int MAX_MMHG = 400;
    private static final BigDecimal MAX_CMHG = new BigDecimal("30");

    private final int systolique;
    private final int diastolique;

    private TensionArterielle(int systolique, int diastolique) {
        this.systolique = systolique;
        this.diastolique = diastolique;
    }

    /**
     * Parses a {@code "sys/dia"} textual value (spaces tolerated).
     *
     * @throws BusinessException when the format or the physiological range is invalid
     */
    public static TensionArterielle parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException("La tension artérielle est obligatoire");
        }
        String[] parts = raw.trim().split("/");
        if (parts.length != 2) {
            throw new BusinessException("Format de tension artérielle invalide (attendu: systolique/diastolique)");
        }
        BigDecimal rawSys = parseNumber(parts[0]);
        BigDecimal rawDia = parseNumber(parts[1]);
        // Notation en centimètres de mercure (« 12/8 », « 11,5/7 »), courante en pratique : convertie en mmHg.
        // Aucune systolique réelle n'est ≤ 30 mmHg, donc la notation n'est jamais ambiguë.
        BigDecimal factor = rawSys.compareTo(MAX_CMHG) <= 0 ? BigDecimal.TEN : BigDecimal.ONE;
        int sys = checkRange(rawSys.multiply(factor));
        int dia = checkRange(rawDia.multiply(factor));
        if (sys < dia) {
            throw new BusinessException("La systolique doit être supérieure ou égale à la diastolique");
        }
        return new TensionArterielle(sys, dia);
    }

    /**
     * Lenient variant used on optional input: {@link Optional#empty()} when blank.
     */
    public static Optional<TensionArterielle> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(parse(raw));
    }

    private static BigDecimal parseNumber(String value) {
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new BusinessException("Valeur de tension non numérique: " + value);
        }
    }

    private static int checkRange(BigDecimal mmHg) {
        int rounded = mmHg.setScale(0, RoundingMode.HALF_UP).intValue();
        if (rounded < MIN_MMHG || rounded > MAX_MMHG) {
            throw new BusinessException("Valeur de tension hors bornes physiologiques: " + rounded);
        }
        return rounded;
    }

    public int systolique() {
        return systolique;
    }

    public int diastolique() {
        return diastolique;
    }

    /**
     * Canonical {@code "sys/dia"} representation.
     */
    public String format() {
        return systolique + "/" + diastolique;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TensionArterielle other)) return false;
        return systolique == other.systolique && diastolique == other.diastolique;
    }

    @Override
    public int hashCode() {
        return 31 * systolique + diastolique;
    }

    @Override
    public String toString() {
        return format();
    }
}

