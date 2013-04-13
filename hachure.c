#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#define SIZE 3601
short *elevation;

int cmp(const void *v1, const void *v2) {
	const int *i1 = v1;
	const int *i2 = v2;

	return elevation[*i2] - elevation[*i1];
}

void scale(int x, int y) {
	printf("%.6f %.6f ", x * 612.0 / SIZE, 612 - y * 612.0 / SIZE);
}

void findbest(int x, int y, int e, int *x1, int *y1) {
	*x1 = -1;
	*y1 = -1;
	int e1 = e;

	int xx, yy;

	for (xx = x - 1; xx <= x + 1; xx++) {
		for (yy = y - 1; yy <= y + 1; yy++) {
			if (xx >= 0 && yy >= 0 && xx < SIZE && yy < SIZE) {
				if (elevation[yy * SIZE + xx] < e1 &&
				    elevation[yy * SIZE + xx] != -32768) {
					e1 = elevation[yy * SIZE + xx];

					// printf("for %d %d (%d) found %d %d (%d)\n", x, y, e, xx, yy, e1);

					*x1 = xx;
					*y1 = yy;
				}
			}
		}
	}
}

int main(int argc, char **argv) {
	int *order;

	order = malloc(SIZE * SIZE * sizeof(int));
	elevation = malloc(SIZE * SIZE * sizeof(short));

	int i;
	for (i = 0; i < SIZE * SIZE; i++) {
		int c1 = getchar();
		int c2 = getchar();

		elevation[i] = (c1 << 8) | c2;
		order[i] = i;
	}

	qsort(order, SIZE * SIZE, sizeof(int), cmp);

	printf("0 setlinewidth\n");

	int xs[SIZE];
	int ys[SIZE];

	for (i = 0; i < SIZE * SIZE; i++) {
		if (elevation[order[i]] == -32768) {
			continue;
		}

		int x = order[i] % SIZE;
		int y = order[i] / SIZE;

		scale(x, y);
		printf("moveto ");

		int step = 0;
		while (1) {
			int e = elevation[y * SIZE + x];
			elevation[y * SIZE + x] = -32768;

			xs[step] = x;
			ys[step] = y;
			step++;

			int x1, y1;
			findbest(x, y, e, &x1, &y1);

			if (x1 < 0) {
				printf("stroke %% %d %d\n", x, y);
				break;
			}

			scale(x1, y1);
			printf("lineto ");

			x = x1;
			y = y1;

		}

		int steps = step;
		for (step = 0; step < steps; step++) {
			int x = xs[step];
			int y = ys[step];

			int x1, y1;
			for (x1 = x - sqrt(step); x1 <= x + sqrt(step); x1++) {
				for (y1 = y - sqrt(step); y1 <= y + sqrt(step); y1++) {
					if (x1 >= 0 && y1 >= 0 && x1 < SIZE && y1 < SIZE) {
						elevation[y1 * SIZE + x1] = -32768;
					}
				}
			}
		}
	}

	return 0;
}
