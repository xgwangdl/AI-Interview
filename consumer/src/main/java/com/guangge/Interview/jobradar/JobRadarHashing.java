package com.guangge.Interview.jobradar;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class JobRadarHashing {
    private JobRadarHashing() { }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    static String normalizeText(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').trim().replaceAll("[\\t ]+", " ");
    }
}
