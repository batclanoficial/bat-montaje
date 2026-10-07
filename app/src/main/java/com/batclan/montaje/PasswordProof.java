package com.batclan.montaje;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** PBKDF2 en el dispositivo: nunca se envía la contraseña escrita. */
final class PasswordProof {
    static final int ITERATIONS = 150000;

    private PasswordProof() { }

    static String newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return toHex(salt);
    }

    static String derive(String password, String saltHex, int iterations) throws Exception {
        if (password == null || password.length() < 12 || password.length() > 128 ||
                saltHex == null || !saltHex.matches("[0-9a-fA-F]{32}") ||
                iterations < ITERATIONS || iterations > 600000) {
            throw new IllegalArgumentException("Contraseña inválida.");
        }
        byte[] salt = fromHex(saltHex);
        char[] chars = password.toCharArray();
        try {
            PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
            try {
                return toHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded());
            } catch (NoSuchAlgorithmException unavailable) {
                return deriveFallback(chars, salt, iterations);
            } finally {
                spec.clearPassword();
            }
        } finally {
            Arrays.fill(chars, '\0');
            Arrays.fill(salt, (byte) 0);
        }
    }

    private static String deriveFallback(char[] chars, byte[] salt, int iterations) throws Exception {
        byte[] passwordBytes = new String(chars).getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
            byte[] block = Arrays.copyOf(salt, salt.length + 4);
            block[block.length - 1] = 1;
            byte[] current = mac.doFinal(block);
            byte[] result = current.clone();
            for (int i = 1; i < iterations; i++) {
                current = mac.doFinal(current);
                for (int j = 0; j < result.length; j++) result[j] ^= current[j];
            }
            Arrays.fill(current, (byte) 0);
            Arrays.fill(block, (byte) 0);
            return toHex(result);
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private static String toHex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            output[i * 2] = digits[(bytes[i] >>> 4) & 15];
            output[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(output);
    }
}
