package dev.heliosares.sync.net;

import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

public class EncryptionManager {

	private static final String RSA = "RSA";
	private static Key KEY;

	public static void setKey(String key, boolean priv) throws InvalidKeySpecException, NoSuchAlgorithmException {
		if (priv) {
			KEY = KeyFactory.getInstance(RSA).generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key)));
		} else {
			KEY = KeyFactory.getInstance(RSA).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
		}
	}

	public static KeyPair generateRSAKkeyPair() {
		try {
			SecureRandom secureRandom = new SecureRandom();
			KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(RSA);

			keyPairGenerator.initialize(2048, secureRandom);
			return keyPairGenerator.generateKeyPair();
		} catch (Exception ignored) {
		}
		return null;
	}

	protected static byte[] encrypt(String str) {
		return encrypt(str.getBytes());
	}

	protected static byte[] encrypt(byte[] plainText) {
		try {
			Cipher cipher = Cipher.getInstance(RSA);
			cipher.init(Cipher.ENCRYPT_MODE, KEY);
			return cipher.doFinal(plainText);
		} catch (Exception e) {
		}
		return null;
	}

	protected static byte[] decryptByte(byte[] cipherText) {
		try {
			Cipher cipher = Cipher.getInstance(RSA);
			cipher.init(Cipher.DECRYPT_MODE, KEY);
			byte[] result = cipher.doFinal(cipherText);
			return result;
		} catch (Exception e) {
		}
		return null;
	}

	protected static String decryptString(byte[] cipherText) {
		return new String(decryptByte(cipherText));
	}

	public static String encode(byte[] bytes) {
		return new String(Base64.getEncoder().encode(bytes));
	}

	public static byte[] decode(String str) {
		return Base64.getDecoder().decode(str);
	}
}
