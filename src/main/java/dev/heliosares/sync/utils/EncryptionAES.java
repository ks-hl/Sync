package dev.heliosares.sync.utils;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

public class EncryptionAES {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int IV_BITS = 128;
    private static final int IV_BYTES = IV_BITS / 8;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final SecretKey key;
    private final Cipher cipherEncrypt;
    private final Cipher cipherDecrypt;

    private Cipher createCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key, decodeIV(generateIV())); // not strictly necessary, but pre-validates the key
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Cipher initialization failed", e);
        }
    }

    public EncryptionAES(SecretKey key) {
        this.key = key;
        this.cipherEncrypt = createCipher(Cipher.ENCRYPT_MODE);
        this.cipherDecrypt = createCipher(Cipher.DECRYPT_MODE);
    }

    public EncryptionAES(byte[] key) {
        this(new SecretKeySpec(key, "AES"));
    }

    public byte[] encodeKey() {
        return key.getEncoded();
    }

    public static SecretKey generateRandomKey() {
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES not implemented");
        }
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    private static byte[] generateIV() {
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        return iv;
    }

    private static GCMParameterSpec decodeIV(byte[] bytes) {
        return new GCMParameterSpec(IV_BITS, bytes);
    }

    public byte[] encrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        byte[] ivBytes = generateIV();
        byte[] cipherText;
        synchronized (cipherEncrypt) {
            try {
                cipherEncrypt.init(Cipher.ENCRYPT_MODE, key, decodeIV(ivBytes));
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                assert false;
            }
            cipherText = cipherEncrypt.doFinal(bytes);
        }
        byte[] out = new byte[IV_BYTES + cipherText.length];
        System.arraycopy(ivBytes, 0, out, 0, IV_BYTES);
        System.arraycopy(cipherText, 0, out, IV_BYTES, cipherText.length);
        return out;
    }

    public byte[] decrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        GCMParameterSpec iv = decodeIV(Arrays.copyOfRange(bytes, 0, IV_BYTES));
        synchronized (cipherDecrypt) {
            try {
                cipherDecrypt.init(Cipher.DECRYPT_MODE, key, iv);
            } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
                assert false;
            }
            return cipherDecrypt.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
        }
    }
}
