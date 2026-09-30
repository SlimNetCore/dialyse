package com.hemodialyse.backend.infrastructure.reporting;

import net.sf.jasperreports.engine.util.DefaultFormatFactory;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats Jasper des documents : en français, le séparateur de milliers du JDK est l'espace fine insécable
 * (U+202F), absente des polices PDF standard (le chiffre « 1 200 » deviendrait « 1200 » ou un carré). Elle est
 * remplacée par l'espace insécable classique (U+00A0), présente dans toutes les polices.
 */
public class FrenchFormatFactory extends DefaultFormatFactory {

    @Override
    public NumberFormat createNumberFormat(String pattern, Locale locale) {
        NumberFormat format = super.createNumberFormat(pattern, locale);
        if (format instanceof DecimalFormat decimal) {
            DecimalFormatSymbols symbols = decimal.getDecimalFormatSymbols();
            if (symbols.getGroupingSeparator() == '\u202F') {
                symbols.setGroupingSeparator('\u00A0');
                decimal.setDecimalFormatSymbols(symbols);
            }
        }
        return format;
    }
}

