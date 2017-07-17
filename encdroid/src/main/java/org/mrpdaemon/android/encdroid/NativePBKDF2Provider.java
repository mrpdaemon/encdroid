package org.mrpdaemon.android.encdroid;

import org.mrpdaemon.sec.encfs.EncFSPBKDF2Provider;

import android.util.Log;

public class NativePBKDF2Provider extends EncFSPBKDF2Provider {

	static {
		try {
			System.loadLibrary("pbkdf2");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	native byte[] pbkdf2(String password, int saltLen, byte[] salt,
			int iterations, int keyLen);

	/* calling here forces class initialization */
	public static void checkAvailable() {
	}

	@Override
	public byte[] doPBKDF2(String password, int saltLen, byte[] salt,
			int iterations, int keyLen) {
		Log.d("NativePBKDF2Provider", "Calling into native PBKDF2 function!");
		return pbkdf2(password, saltLen, salt, iterations, keyLen);
	}
}
