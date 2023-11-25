package dev.heliosares.sync.encryption;

import dev.heliosares.sync.utils.EncryptionAES;
import dev.heliosares.sync.utils.EncryptionRSA;
import org.junit.Test;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.security.InvalidKeyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestEncryption {
    private static final String msg = "Hello world";

    @Test
    public void testAES() throws IllegalBlockSizeException, BadPaddingException {
        EncryptionAES aes1 = new EncryptionAES(EncryptionAES.generateKey(), EncryptionAES.generateIv());
        EncryptionAES aes2 = new EncryptionAES(EncryptionAES.generateKey(), EncryptionAES.generateIv());
        assertEquals(msg, new String(aes1.decrypt(aes1.encrypt(msg.getBytes()))));
        assertEquals(msg, new String(aes2.decrypt(aes2.encrypt(msg.getBytes()))));
        assertEquals(msg, new String(aes1.decrypt(aes1.encrypt(msg.getBytes()))));
        assertThrows(IllegalStateException.class, () -> aes1.decrypt(aes2.encrypt(msg.getBytes())));
    }

    @Test
    public void testRSA() throws InvalidKeyException {
        EncryptionRSA.RSAPair pair = EncryptionRSA.generate();
        assertEquals(msg, new String(pair.privateKey().decrypt(pair.publicKey().encrypt(msg.getBytes()))));
        assertEquals(msg, new String(pair.publicKey().decrypt(pair.privateKey().encrypt(msg.getBytes()))));
        assertThrows(InvalidKeyException.class, () -> pair.publicKey().decrypt(pair.publicKey().encrypt(msg.getBytes())));
        assertThrows(InvalidKeyException.class, () -> pair.privateKey().decrypt(pair.privateKey().encrypt(msg.getBytes())));
    }
}
