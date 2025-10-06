package com.hstahlmann.dbuserprovider.util;

import org.apache.commons.codec.binary.Hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public class AuthCredentials {

    public static Map<String, String> generate(String password, String hashingAlgorithm) {
        String salt = generateSalt();
        String hash = generateHash(password + salt, hashingAlgorithm);
        for (int i = 1; i <= 1024; i++) {
            hash = generateHash(hash + salt, hashingAlgorithm);
        }
       return Map.of("hash", hash, "salt", salt);
    }

    public static boolean validate(String password, String salt, String storedHash, String hashingAlgorithm) {
        String hash = generateHash(password + salt, hashingAlgorithm);
        for (int i = 1; i <= 1024; i++) {
            hash = generateHash(hash + salt, hashingAlgorithm);
        }
        return hash.equals(storedHash);
    }

    private static String generateSalt() {
        int size = 16;

        byte[] b = new byte[size];
        new SecureRandom().nextBytes(b);

        return Base64.getEncoder().encodeToString(b);
    }

    private static String generateHash(String input, String hashingAlgorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(hashingAlgorithm);

            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            return Hex.encodeHexString(messageDigest).toUpperCase();
        }

        // For specifying wrong message digest algorithms or unsupported encodings
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
