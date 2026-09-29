package com.tec.dnsapi.validation;

import org.xbill.DNS.Address;

/** Utilidades para IPs literales (IPv4/IPv6). No hace consultas DNS. */
public final class IpAddresses {

    private IpAddresses() {}

    /** Devuelve los bytes de la IP, o null si el texto no es una IP literal válida. */
    public static byte[] parse(String text) {
        if (text == null) {
            return null;
        }
        String ip = text.trim();
        byte[] bytes = Address.toByteArray(ip, Address.IPv4);
        return bytes != null ? bytes : Address.toByteArray(ip, Address.IPv6);
    }

    public static boolean isValid(String text) {
        return parse(text) != null;
    }

    /** Compara dos IPs de la misma familia como enteros sin signo. */
    public static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int diff = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diff != 0) {
                return diff;
            }
        }
        return 0;
    }
}
