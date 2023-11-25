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

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private final SecretKey key;
    private final GCMParameterSpec iv;

    private final Cipher cipherEncrypt;
    private final Cipher cipherDecrypt;

    private Cipher createCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(mode, key, iv);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Cipher initialization failed", e);
        }
    }

    public EncryptionAES(SecretKey key, GCMParameterSpec iv) {
        this.key = key;
        this.iv = iv;
        this.cipherEncrypt = createCipher(Cipher.ENCRYPT_MODE);
        this.cipherDecrypt = createCipher(Cipher.DECRYPT_MODE);
    }

    public EncryptionAES(byte[] encodedKey) {
        this(new SecretKeySpec(Arrays.copyOfRange(encodedKey, 0, encodedKey.length - 16), "AES"), new GCMParameterSpec(128, Arrays.copyOfRange(encodedKey, encodedKey.length - 16, encodedKey.length)));
    }

    public byte[] encodeKey() {
        byte[] key = this.key.getEncoded();
        byte[] iv = this.iv.getIV();
        byte[] out = new byte[key.length + iv.length];

        System.arraycopy(key, 0, out, 0, key.length);
        System.arraycopy(iv, 0, out, key.length, iv.length);
        return out;
    }

    public static SecretKey generateKey() {
        KeyGenerator keyGenerator;
        try {
            keyGenerator = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("AES not implemented");
        }
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    public static GCMParameterSpec generateIv() {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new GCMParameterSpec(128, iv);
    }

    public byte[] encrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        synchronized (cipherEncrypt) {
            System.out.println("encrypt");
            try {
                return cipherEncrypt.doFinal(bytes);
            } finally {
                System.out.println("Done encrypt");
            }
        }
    }

    public byte[] decrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        synchronized (cipherDecrypt) {
            System.out.println("decrypt");
            try {
                return cipherDecrypt.doFinal(bytes);
            } finally {
                System.out.println("Done decrypt");
            }
        }
    }
}
