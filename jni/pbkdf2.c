/*
 * encdroid - EncFS client application for Android
 * Copyright (C) 2012  Mark R. Pariente
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <jni.h>

#include <openssl/evp.h>

jbyteArray
Java_org_mrpdaemon_android_encdroid_EDNativePBKDF2Provider_pbkdf2(JNIEnv *env,
		                                                          jobject jobj,
		                                                          jint pwd_len,
		                                                          jstring password,
		                                                          jint salt_len,
		                                                          jbyteArray salt_data,
		                                                          jint iterations,
		                                                          jint key_len) {
	jbyteArray ret;

	// Grab pointers to the actual data for the array input parameters
	const char *in_password = (*env)->GetStringUTFChars(env, password, 0);
	const jbyte *in_salt_data = (*env)->GetByteArrayElements(env, salt_data, 0);

	// Allocate and prepare the output parameter
	ret = (*env)->NewByteArray(env, key_len);
	jbyte *out_bytes = (*env)->GetByteArrayElements(env, ret, 0);

	if (PKCS5_PBKDF2_HMAC_SHA1(in_password, pwd_len, in_salt_data, salt_len,
			                   iterations, key_len, out_bytes) != 0) {
		(*env)->ReleaseStringUTFChars(env, password, in_password);
		(*env)->ReleaseByteArrayElements(env, ret, out_bytes, 0);

		return ret;
	}

	(*env)->ReleaseStringUTFChars(env, password, in_password);
	(*env)->ReleaseByteArrayElements(env, ret, out_bytes, JNI_ABORT);

	return NULL;
}
