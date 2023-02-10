package dev.heliosares.sync.utils;

// Java Program to Implement the RSA Algorithm

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

public class EncryptionRSA {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public EncryptionRSA(PrivateKey key) {
        this.privateKey = key;
        this.publicKey = null;
    }

    public EncryptionRSA(PublicKey key) {
        this.publicKey = key;
        this.privateKey = null;
    }

    public static PublicKey loadPublicKey(File file) throws FileNotFoundException, InvalidKeySpecException {
        try (Scanner scanner = new Scanner(file)) {
            String keyString = scanner.nextLine();
            KeyFactory publicKeyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(keyString));
            return publicKeyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException e) {
            assert false;
            throw new RuntimeException("RSA algorithm not implemented");
        }
    }

    public static PrivateKey loadPrivateKey(File file) throws FileNotFoundException, InvalidKeySpecException {
        try (Scanner scanner = new Scanner(file)) {
            String keyString = scanner.nextLine();
            KeyFactory privateKeyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(keyString));
            return privateKeyFactory.generatePrivate(privateKeySpec);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not implemented");
        }
    }

    public static void write(File file, Key key) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(Base64.getEncoder().encodeToString(key.getEncoded()));
        }
    }

    public static KeyPair generate() {
        KeyPairGenerator kpg = null;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            assert false : "RSA algorithm not implemented";
        }
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    public byte[] encode(byte[] bytes) throws InvalidKeyException {
        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not implemented");
        } catch (NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new InvalidKeyException(e);
        }
    }

    public byte[] decode(byte[] bytes) throws InvalidKeyException {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.DECRYPT_MODE, getKey());
            return cipher.doFinal(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not implemented");
        } catch (NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
            throw new InvalidKeyException(e);
        }
    }

    public Key getKey() {
        if (privateKey != null) return privateKey;
        return publicKey;
    }
}