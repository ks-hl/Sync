package dev.heliosares.sync.utils;

import javax.crypto.*;
import javax.crypto.spec.DHParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class EncryptionDH {
    private static final String ALGO = "DH";

    public static void main(String[] args) throws GeneralSecurityException, IOException {
        AlgorithmParameters params = generateParameters();

        KeyPair alice_key = generate(params);
        KeyPair bob_key = generate(generateParameters(params.getEncoded()));

        System.out.println(Base64.getEncoder().encodeToString(params.getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(combine(alice_key.getPrivate(), bob_key.getPublic()).getEncoded()));
        System.out.println(Base64.getEncoder().encodeToString(combine(bob_key.getPrivate(), alice_key.getPublic()).getEncoded()));
        SecretKey key = combine(alice_key.getPrivate(), bob_key.getPublic());
        System.out.println(new String(decrypt(key, encrypt(key, "test".getBytes()))));
    }

    public static PublicKey getPublicKey(byte[] bytes) throws InvalidKeySpecException {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance(ALGO);
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            return keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
        }
    }


    public static AlgorithmParameters generateParameters() {
        try {
            AlgorithmParameterGenerator paramGen = AlgorithmParameterGenerator.getInstance(ALGO);
            paramGen.init(1024);
            return paramGen.generateParameters();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
        }
    }

    public static AlgorithmParameters generateParameters(byte[] encoded) throws IOException {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance(ALGO);
            params.init(encoded);
            return params;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
        }
    }

    public static KeyPair generate(AlgorithmParameters params) throws GeneralSecurityException {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGO);
            keyGen.initialize(params.getParameterSpec(DHParameterSpec.class));
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
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
            e.printStackTrace();
            throw new RuntimeException("Algorithm not implemented");
        }
    }

    public static byte[] encrypt(SecretKey key, byte[] bytes) throws InvalidKeyException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
        } catch (NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new InvalidKeyException(e);
        }
    }

    public static byte[] decrypt(SecretKey key, byte[] bytes) throws InvalidKeyException {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not implemented");
        } catch (NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new InvalidKeyException(e);
        }
    }
}