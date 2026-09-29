package com.tec.dnsapi.validation;

/** Forma canónica de un nombre de dominio, tal como se guarda en la tabla records. */
public final class DomainNames {

    private DomainNames() {}

    /** Recorta espacios, pasa a minúsculas y quita el punto final. Un nulo se convierte en "". */
    public static String normalize(String domain) {
        if (domain == null) {
            return "";
        }
        String trimmed = domain.trim().toLowerCase();
        return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
