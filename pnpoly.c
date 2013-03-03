#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <string.h>
#include <ctype.h>
#include <math.h>

#define BUCKET 0.1

float g_xmin = 0;
float g_ymin = 0;
float g_xmax = 0;
float g_ymax = 0;
int g_any = 0;

struct poly {
	char *description;
	int nvert;
	float *vertx;
	float *verty;

	float xmin;
	float ymin;
	float xmax;
	float ymax;
};

struct polylist {
	struct poly *poly;
	struct polylist *next;
};

struct tree {
	int hash;
	int x;
	int y;
	struct polylist *list;
	struct tree *left;
	struct tree *right;
};

#define HASH_MASK 0xFFFF

struct tree *root[HASH_MASK + 1] = { NULL };

/*
 * http://www.ecse.rpi.edu/~wrf/Research/Short_Notes/pnpoly.html#The C Code
 *
 * Copyright (c) 1970-2003, Wm. Randolph Franklin
 * 
 * Permission is hereby granted, free of charge, to any person obtaining 
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject
 * to the following conditions:
 * 
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimers.
 * 
 * Redistributions in binary form must reproduce the above copyright notice
 * in the documentation and/or other materials provided with the distribution.
 * 
 * The name of W. Randolph Franklin may not be used to endorse or promote
 * products derived from this Software without specific prior written
 * permission.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *
 */
int pnpoly(int nvert, float *vertx, float *verty, float testx, float testy)
{
  int i, j, c = 0;
  for (i = 0, j = nvert-1; i < nvert; j = i++) {
    if ( ((verty[i]>testy) != (verty[j]>testy)) &&
	 (testx < (vertx[j]-vertx[i]) * (testy-verty[i]) / (verty[j]-verty[i]) + vertx[i]) )
       c = !c;
  }
  return c;
}

void *cmalloc(size_t size) {
	void *ret = malloc(size);
	if (ret == NULL) {
		fprintf(stderr, "Can't allocate %d\n", size);
		exit(EXIT_FAILURE);
	}
	return ret;
}

char *fgetl(FILE *f) {
	char *s = cmalloc(2000);
	int max = 2000;
	int len = 0;
	int c;

	*s = '\0';

	while (1) {
		c = fgetc(f);

		if (c < 0) {
			if (len == 0) {
				free(s);
				s = NULL;
				return NULL;
			} else {
				break;
			}
		} else {
			if (len + 2 >= max) {
				s = realloc(s, len + 2000);

				// printf("allocate for %d\n", len + 2000);

				if (s == NULL) {
					fprintf(stderr, "can't allocate %d\n", len + 2000);
					exit(1);
				}

				max = len + 2000;
			}

			// printf("store at %d\n", len);

			s[len++] = c;
			if (c == '\n') {
				break;
			}
		}
	}

	s[len] = '\0';

	return s;
}

void dump(struct tree *t) {
	if (t != NULL) {
		dump(t->left);
		dump(t->right);

		printf("tree %d %d: ", t->x, t->y);

		struct polylist *l = t->list;
		while (l != NULL)  {
			printf("%f,%f to %f,%f ", l->poly->xmin, l->poly->ymin, l->poly->xmax, l->poly->ymax);
			l = l->next;
		}

		printf("\n");
	}
}

int makehash(int x, int y) {
	return x * 37 + y;
}

void process(FILE *f, char *fname) {
	fprintf(stderr, "reading %s\n", fname);
	int max = 2000;

	float *x = cmalloc(max * sizeof(float)), *y = cmalloc(max * sizeof(float));
	char *s;

	int line = 0;

	while ((s = fgetl(f))) {
		//printf("got %s\n", s);
		line++;

		if (strlen(s) > max) {
			max = strlen(s);

			free(x);
			x = cmalloc(max * sizeof(float));
			free(y);
			y = cmalloc(max * sizeof(float));
		}

		char *cp = strchr(s, ':');
		if (cp != NULL) {
			int count = 0;
			int any = 0;

			float xmin = 0, xmax = 0, ymin = 0, ymax = 0;

			*cp = '\0';
			cp++;
			while (*cp && isspace(*cp)) {
				cp++;
			}

			while (*cp) {
				char *one = cp;

				if (*cp == '-') {
					cp++;
				}
				while (*cp && isdigit(*cp)) {
					cp++;
				}
				if (*cp == '.') {
					cp++;
				}
				while (*cp && isdigit(*cp)) {
					cp++;
				}

				if (*cp != ',') {
					fprintf(stderr, "%s: %d: expecting comma or digit, not %s\n", fname, line, cp);
					goto next;
				}
				cp++;

				while (*cp && isspace(*cp)) {
					cp++;
				}

				char *two = cp;

				if (*cp == '-') {
					cp++;
				}
				while (*cp && isdigit(*cp)) {
					cp++;
				}
				if (*cp == '.') {
					cp++;
				}
				while (*cp && isdigit(*cp)) {
					cp++;
				}

				while (*cp && isspace(*cp)) {
					cp++;
				}

				float a = atof(one);
				float b = atof(two);

				x[count] = a;
				y[count] = b;

				if (a != 0 || b != 0) {
					if (any == 0 || a < xmin) {
						xmin = a;

						if (g_any == 0 || a < g_xmin) {
							g_xmin = a;
						}
					}
					if (any == 0 || a > xmax) {
						xmax = a;

						if (g_any == 0 || a > g_xmax) {
							g_xmax = a;
						}
					}
					if (any == 0 || b < ymin) {
						ymin = b;

						if (g_any == 0 || b < g_ymin) {
							g_ymin = b;
						}
					}
					if (any == 0 || b > ymax) {
						ymax = b;

						if (g_any == 0 || b > g_ymax) {
							g_ymax = b;
						}
					}

					any = 1;
					g_any = 1;
				}

				count++;
			}

#if 0
			if (xmax - xmin > 1 || ymax - ymin > 1) {
				fprintf(stderr, "Implausible bounds %f,%f to %f,%f\n", xmin, ymin, xmax, ymax);
				goto next;
			}
#endif

			float *xs = cmalloc(count * sizeof(float));
			float *ys = cmalloc(count * sizeof(float));

			memcpy(xs, x, count * sizeof(float));
			memcpy(ys, y, count * sizeof(float));

			struct poly *poly = cmalloc(sizeof(struct poly));

			poly->description = strdup(s);

			poly->vertx = xs;
			poly->verty = ys;
			poly->nvert = count;

			poly->xmin = xmin;
			poly->ymin = ymin;
			poly->xmax = xmax;
			poly->ymax = ymax;

			// printf("%s: %f %f to %f %f\n", s, xmin, ymin, xmax, ymax);

			int minx = floor(xmin / BUCKET);
			int miny = floor(ymin / BUCKET);
			int maxx = ceil(xmax / BUCKET);
			int maxy = ceil(ymax / BUCKET);

			// printf("is %d %d to %d %d\n", minx, miny, maxx, maxy);

			{
				int x, y;

				for (x = minx; x <= maxx; x++) {
					for (y = miny; y <= maxy; y++) {
						int hash = makehash(x, y);
						struct tree **t = &root[hash & HASH_MASK];

						while (*t != NULL) {
							int cmp;
							cmp = (*t)->x - x;
							if (cmp == 0) {
								cmp = (*t)->y - y;
							}

							if (cmp == 0) {
								break;
							}
							if (cmp < 0) {
								t = &((*t)->left);
							} else {
								t = &((*t)->right);
							}
						}

						if (*t == NULL) {
							*t = cmalloc(sizeof(struct tree));

							(*t)->hash = hash;
							(*t)->x = x;
							(*t)->y = y;
							(*t)->list = NULL;
							(*t)->left = NULL;
							(*t)->right = NULL;
						}

						struct polylist *pl = cmalloc(sizeof(struct polylist));
						pl->poly = poly;
						pl->next = (*t)->list;
						(*t)->list = pl;
					}
				}
			}
		} else {
			float a = atof(s);
			char *cp = s;

			if (*cp == '-') {
				cp++;
			}
			while (*cp && isdigit(*cp)) {
				cp++;
			}
			if (*cp == '.') {
				cp++;
			}
			while (*cp && isdigit(*cp)) {
				cp++;
			}

			if (*cp != ',') {
				fprintf(stderr, "%s: %d: expecting comma or digit, not %s\n", fname, line, cp);
				goto next;
			}
			cp++;

			while (*cp && isspace(*cp)) {
				cp++;
			}

			float b = atof(cp);

			if (a < g_xmin || a > g_xmax) {
				goto next;
			}
			if (b < g_ymin || b > g_ymax) {
				goto next;
			}

			int x = floor(a / BUCKET);
			int y = floor(b / BUCKET);
			int hash = makehash(x, y);

			struct tree *t = root[hash & HASH_MASK];
			while (t != NULL) {
				int cmp;
				cmp = t->x - x;
				if (cmp == 0) {
					cmp = t->y - y;
				}

				if (cmp == 0) {
					break;
				}
				if (cmp < 0) {
					t = t->left;
				} else {
					t = t->right;
				}
			}

			if (t != NULL) {
				struct polylist *pl;

				for (pl = t->list; pl != NULL; pl = pl->next) {
					if (a >= pl->poly->xmin && a <= pl->poly->xmax && b >= pl->poly->ymin && b <= pl->poly->ymax) {
						// printf("maybe: %f %f in %f %f %f %f\n", a, b,
						//	pl->poly->xmin, pl->poly->ymin, pl->poly->xmax, pl->poly->ymax);

						if (pnpoly(pl->poly->nvert, pl->poly->vertx, pl->poly->verty, a, b)) {
							printf("%s: %s", pl->poly->description, s);
							//printf("%f,%f: %s: %s", a, b, pl->poly->description, s);
							goto next;
						}
					}
				}
			}

			// printf("%f,%f: null\n", a, b);
		}

	next:
		free(s);
		s = NULL;
	}

	free(x);
	x = NULL;
	free(y);
	y = NULL;

	// dump(root);
}

int main(int argc, char **argv) {
	int fail = 0;

	if (argc < 2) {
		process(stdin, "standard input");
	} else {
		int i;

		for (i = 1; i < argc; i++) {
			FILE *f = fopen(argv[i], "r");

			if (f != NULL) {
				process(f, argv[i]);
				fclose(f);
			} else {
				fprintf(stderr, "%s: %s: %s\n", argv[0], argv[i], strerror(errno));
				fail = 1;
			}
		}
	}

	return fail;
}
