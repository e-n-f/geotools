#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <unistd.h>

void usage(char **argv) {
	fprintf(stderr, "Usage: %s [-n count] file ...\n", argv[0]);
	exit(EXIT_FAILURE);
}

void sample(char *fname, int samples) {
	int fd = open(fname, O_RDONLY);
	if (fd < 0) {
		perror(fname);
		exit(EXIT_FAILURE);
	}

	struct stat st;
	if (fstat(fd, &st) != 0) {
		perror("stat");
		exit(EXIT_FAILURE);
	}

	char *map = mmap(NULL, st.st_size, PROT_READ, MAP_PRIVATE, fd, 0);
	if (map == MAP_FAILED) {
		perror("mmap");
		exit(EXIT_FAILURE);
	}

	int i;
	for (i = 0; i < samples; i++) {
		long long start = i * (long double) st.st_size / samples;
		while (start > 0 && map[start - 1] != '\n') {
			start--;
		}

		while (start < st.st_size) {
			putchar(map[start]);
			if (map[start] == '\n') {
				break;
			}
			start++;
		}
	}

	munmap(map, st.st_size);
	close(fd);
}

int main(int argc, char **argv) {
	int i;
	extern char *optarg;
	extern int optind;
	int samples = 1000;

	while ((i = getopt(argc, argv, "n:")) != -1) {
		switch (i) {
		case 'n':
			samples = atoi(optarg);
			break;

		default:
			usage(argv);
			break;
		}
	}

	if (optind >= argc) {
		usage(argv);
	}

	for (i = optind; i < argc; i++) {
		sample(argv[i], samples);
	}

	exit(EXIT_SUCCESS);
}
