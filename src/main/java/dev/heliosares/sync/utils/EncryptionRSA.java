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
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;
import java.util.UUID;

public class EncryptionRSA {
    private Cipher createCipher(int mode) {
        try {
            Cipher cipher = Cipher.getInstance("RSA");
            cipher.init(mode, key); // not strictly necessary, but pre-validates the key
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            throw new RuntimeException("Cipher initialization failed", e);
        }
    }

    private final UUID uuid;
    private final Key key;
    private final String user;
    private final Cipher encryptCipher;
    private final Cipher decryptCipher;

    private EncryptionRSA(UUID uuid, Key key, String user) {
        this.uuid = uuid;
        this.key = key;
        this.user = user;

        encryptCipher = createCipher(Cipher.ENCRYPT_MODE);
        decryptCipher = createCipher(Cipher.DECRYPT_MODE);
    }

    public static EncryptionRSA load(File file) throws FileNotFoundException, InvalidKeySpecException {
        try (Scanner scanner = new Scanner(file)) {
            String type = scanner.nextLine();
            boolean isPrivate = switch (type) {
                case "private" -> true;
                case "public" -> false;
                default -> throw new InvalidKeySpecException();
            };
            UUID uuid = UUID.fromString(scanner.nextLine());
            String keyString = scanner.nextLine();
            return load(uuid, Base64.getDecoder().decode(keyString), isPrivate, file.getName().substring(0, file.getName().indexOf('.')));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static EncryptionRSA load(UUID uuid, byte[] bytes, boolean isPrivate, String name) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (isPrivate) {
            KeyFactory privateKeyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(bytes);
            return new EncryptionRSA(uuid, privateKeyFactory.generatePrivate(privateKeySpec), name);
        } else {
            KeyFactory publicKeyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(bytes);
            return new EncryptionRSA(uuid, publicKeyFactory.generatePublic(publicKeySpec), name);
        }
    }

    public void write(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8, false)) {
            writer.write((key instanceof PrivateKey ? "private" : "public") + "\n");
            writer.write(uuid.toString() + "\n");
            writer.write(Base64.getEncoder().encodeToString(key.getEncoded()));
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getUser() {
        return user;
    }

    public record RSAPair(EncryptionRSA publicKey, EncryptionRSA privateKey) {
    }

    public static RSAPair generate() {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        kpg.initialize(2048);
        java.security.KeyPair pair = kpg.generateKeyPair();
        UUID uuid = UUID.randomUUID();
        return new RSAPair(new EncryptionRSA(uuid, pair.getPublic(), null), new EncryptionRSA(uuid, pair.getPrivate(), null));
    }

    public byte[] encrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        synchronized (encryptCipher) {
            return encryptCipher.doFinal(bytes);
        }
    }

    public byte[] decrypt(byte[] bytes) throws IllegalBlockSizeException, BadPaddingException {
        synchronized (decryptCipher) {
            return decryptCipher.doFinal(bytes);
        }
    }
}