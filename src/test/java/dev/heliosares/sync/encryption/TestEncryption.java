package dev.heliosares.sync.encryption;

import dev.heliosares.sync.utils.EncryptionAES;
import dev.heliosares.sync.utils.EncryptionDH;
import dev.heliosares.sync.utils.EncryptionRSA;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestEncryption {
    private static final String msg = "Hello world";

    @Test
    public void testAES() throws IllegalBlockSizeException, BadPaddingException {
        EncryptionAES aes1 = new EncryptionAES(EncryptionAES.generateRandomKey());
        EncryptionAES aes2 = new EncryptionAES(EncryptionAES.generateRandomKey());
        for (int i = 0; i < 100; i++) {
            assertEquals(msg, new String(aes1.decrypt(aes1.encrypt(msg.getBytes()))));
            assertEquals(msg, new String(aes2.decrypt(aes2.encrypt(msg.getBytes()))));
        }
        byte[] cipherText1 = aes1.encrypt(msg.getBytes());
        byte[] cipherText2 = aes1.encrypt(msg.getBytes());
        assert !Arrays.equals(cipherText1, 16, cipherText1.length - 16, cipherText2, 16, cipherText2.length);
        assertThrows(GeneralSecurityException.class, () -> aes1.decrypt(aes2.encrypt(msg.getBytes())));
    }

    @Test
    public void testAesWithDifferentSizes() throws IllegalBlockSizeException, BadPaddingException {
        // Test with empty, small, and large inputs
        String[] testMessages = {"", "a", new String(new char[10000]).replace('\0', 'a')};
        for (String testMsg : testMessages) {
            EncryptionAES aes = new EncryptionAES(EncryptionAES.generateRandomKey());
            byte[] encrypted = aes.encrypt(testMsg.getBytes());
            String decrypted = new String(aes.decrypt(encrypted));
            assertEquals(testMsg, decrypted);
        }
    }

    @Test
    public void testRSA() throws IllegalBlockSizeException, BadPaddingException {
        EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
        for (int i = 0; i < 100; i++) {
            assertEquals(msg, new String(pair.privateKey().decrypt(pair.publicKey().encrypt(msg.getBytes()))));
            assertEquals(msg, new String(pair.publicKey().decrypt(pair.privateKey().encrypt(msg.getBytes()))));
        }
        assertThrows(GeneralSecurityException.class, () -> pair.publicKey().decrypt(pair.publicKey().encrypt(msg.getBytes())));
        assertThrows(GeneralSecurityException.class, () -> pair.privateKey().decrypt(pair.privateKey().encrypt(msg.getBytes())));
    }

    @Test
    public void testDH() throws GeneralSecurityException, IOException {
        AlgorithmParameters params = EncryptionDH.generateParameters();

        KeyPair alice_key = EncryptionDH.generate(params);
        KeyPair bob_key = EncryptionDH.generate(EncryptionDH.generateParameters(params.getEncoded()));

        SecretKey key = EncryptionDH.combine(alice_key.getPrivate(), bob_key.getPublic());

        assertEquals(msg, new String(EncryptionDH.decrypt(key, EncryptionDH.encrypt(key, msg.getBytes()))));
    }

    @Test
    public void testDhParameterConsistency() throws GeneralSecurityException, IOException {
        AlgorithmParameters params1 = EncryptionDH.generateParameters();
        AlgorithmParameters params2 = EncryptionDH.generateParameters(params1.getEncoded());

        KeyPair alice_key = EncryptionDH.generate(params1);
        KeyPair bob_key = EncryptionDH.generate(params2);

        SecretKey key1 = EncryptionDH.combine(alice_key.getPrivate(), bob_key.getPublic());
        SecretKey key2 = EncryptionDH.combine(bob_key.getPrivate(), alice_key.getPublic());

        assertEquals(key1, key2);
    }
}
