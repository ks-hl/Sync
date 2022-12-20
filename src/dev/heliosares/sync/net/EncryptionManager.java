package dev.heliosares.sync.net;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EncryptionManager {

    private static final String RSA = "RSA";
    private static final String AES = "AES/CBC/PKCS5Padding";
    private static Key RSA_KEY;
    private static IvParameterSpec iv;
    private static SecretKey AES_KEY;

    public static void setRSAkey(String key, boolean priv) throws InvalidKeySpecException, NoSuchAlgorithmException {
        if (priv) {
            RSA_KEY = KeyFactory.getInstance(RSA)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
        } else {
            RSA_KEY = KeyFactory.getInstance(RSA)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
        }
    }

    public static KeyPair generateRSAKkeyPair() throws GeneralSecurityException {
        SecureRandom secureRandom = new SecureRandom();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA);

        keyPairGenerator.initialize(2048, secureRandom);
        return keyPairGenerator.generateKeyPair();
    }

    public static SecretKey generateAESkey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return AES_KEY = keyGenerator.generateKey();
    }

    public static void generateIV() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        iv = new IvParameterSpec(bytes);
    }

    public static String encryptAES(String input) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.ENCRYPT_MODE, AES_KEY, iv);
        byte[] cipherText = cipher.doFinal(input.getBytes());
        return encode(cipherText);
    }

    public static String decryptAES(String cipherText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES);
        cipher.init(Cipher.DECRYPT_MODE, AES_KEY, iv);
        byte[] plainText = cipher.doFinal(decode(cipherText));
        return new String(plainText);
    }

    public static String encode(byte[] bytes) {
        return new String(Base64.getEncoder().encode(bytes));
    }

    public static byte[] decode(String str) {
        return Base64.getDecoder().decode(str);
    }

    public static long hash(String str) {
        long out = 0;
        for (int i = 0; i < str.length(); i++) {
            out += str.charAt(0) * Math.pow(31, str.length() - 1);
        }
        return out;
    }

    protected static byte[] encryptRSA(String str) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA);
        cipher.init(Cipher.ENCRYPT_MODE, RSA_KEY);
        return cipher.doFinal(str.getBytes());
    }

    protected static String decryptRSA(byte[] cipherText) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA);
        cipher.init(Cipher.DECRYPT_MODE, RSA_KEY);
        return new String(cipher.doFinal(cipherText));
    }

    @SuppressWarnings("unused")
    private static byte[][] split(byte[] in) {
        byte[] iv = new byte[16];
        byte[] cipher = new byte[in.length - 16];
        for (int i = 0; i < in.length; i++) {
            if (i < 16)
                iv[i] = in[i];
            else
                cipher[i - 16] = in[i];
        }
        return new byte[][]{iv, cipher};
    }

    @SuppressWarnings("unused")
    private static byte[] combine(byte[] one, byte[] two) {
        byte[] out = new byte[one.length + two.length];
        for (int i = 0; i < out.length; i++) {
            if (i < one.length) {
                out[i] = one[i];
            } else {
                out[i] = two[i - one.length];
            }
        }
        return out;
    }
}
