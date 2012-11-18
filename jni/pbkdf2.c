#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <openssl/evp.h>

#define KEY_LEN      32
#define KEK_KEY_LEN  24
#define ITERATION    1161684 

int main() {
	size_t i;
	unsigned char *out;
	const char pwd[] = "test";
	unsigned char salt_value[] = { 0, 0, 0, 0, 0, 0, 5, 0, 0, 3, 0, 0, 0, 0, 0,
			7, 0, 0, 0, 0 };

	out = (unsigned char *) malloc(sizeof(unsigned char) * KEK_KEY_LEN);

	printf("pass: %s\n", pwd);
	printf("ITERATION: %u\n", ITERATION);
	printf("salt: ");
	for (i = 0; i < sizeof(salt_value); i++) {
		printf("%02x", salt_value[i]);
	}
	printf("\n");

	if (PKCS5_PBKDF2_HMAC_SHA1(pwd, strlen(pwd), salt_value, sizeof(salt_value),
			ITERATION, KEK_KEY_LEN, out) != 0) {
		printf("out: ");
		for (i = 0; i < KEK_KEY_LEN; i++) {
			printf("%02x", out[i]);
		}
		printf("\n");
	} else {
		//fprintf(stderr, "PKCS5_PBKDF2_HMAC_SHA1 failed\n");
	}

	free(out);
	return 0;
}
