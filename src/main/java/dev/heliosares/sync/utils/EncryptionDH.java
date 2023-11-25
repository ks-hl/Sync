package dev.heliosares.sync.utils;

import javax.crypto.*;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class EncryptionDH {
    private static final String ALGO = "DH";

    public static PublicKey getPublicKey(byte[] bytes) throws InvalidKeySpecException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGO);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }


    public static AlgorithmParameters generateParameters() {
        try {
            AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance(ALGO);
            paramGen.init(1024);
            return paramGen.generateParameters();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static AlgorithmParameters generateParameters(byte[] encoded) throws IOException {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance(ALGO);
            params.init(encoded);
            return params;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair generate(AlgorithmParameters params) throws GeneralSecurityException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGO);
            keyGen.initialize(params.getParameterSpec(DHParameterSpec.class));
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static SecretKey combine(PrivateKey private1, PublicKey public1) throws InvalidKeyException {
        try {
            KeyAgreement ka = KeyAgreement.getInstance(ALGO);
            ka.init(private1);
            ka.doPhase(public1, true);
            byte[] sharedSecret = ka.generateSecret();
            return new SecretKeySpec(sharedSecret, 0, 32, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] encrypt(SecretKey key, byte[] bytes) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] bytes) throws InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }
}