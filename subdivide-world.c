#include <stdio.h>
#include <stdlib.h>
#include <string.h>

struct point {
	int lat;
	int lon;
};

int latcmp(const void *v1, const void *v2) {
	const struct point *p1 = v1;
	const struct point *p2 = v2;

	return p1->lat - p2->lat;
}

int loncmp(const void *v1, const void *v2) {
	const struct point *p1 = v1;
	const struct point *p2 = v2;

	return p1->lon - p2->lon;
}

void out(int minlat, int minlon, int maxlat, int maxlon) {
	printf("%.6f %.6f moveto %.6f %.6f lineto stroke\n",
		(minlon + 180000000) * 612.0 / 360000000,
		(minlat + 90000000) * 612.0 / 360000000,
		(maxlon + 180000000) * 612.0 / 360000000,
		(maxlat + 90000000) * 612.0 / 360000000);
}

void subdivide(struct point *points, int count, int minlat, int minlon, int maxlat, int maxlon) {
	if (count < 1000 || maxlat - minlat < 100000 || maxlon - minlon < 100000) {
		return;
	}

	if (maxlat - minlat > maxlon - minlon) {
		qsort(points, count, sizeof(struct point), latcmp);
		int midlat = points[count / 2].lat;
		out(midlat, minlon, midlat, maxlon);

		subdivide(points, count / 2, minlat, minlon, midlat, maxlon);
		subdivide(points + count / 2, count - count / 2, midlat, minlon, maxlat, maxlon);
	} else {
		qsort(points, count, sizeof(struct point), loncmp);
		int midlon = points[count / 2].lon;
		out(minlat, midlon, maxlat, midlon);

		subdivide(points, count / 2, minlat, minlon, maxlat, midlon);
		subdivide(points + count / 2, count - count / 2, minlat, midlon, maxlat, maxlon);
	}
}

int main() {
	char s[2000];
	char user[2000], date[2000], time[2000];

	struct point *points = NULL;
	int n = 0;
	int max = 0;

	printf("0 setlinewidth\n");

	while (fgets(s, 2000, stdin)) {
		float lat, lon;
		if (sscanf(s, "%s %s %s %f,%f", user, date, time, &lat, &lon) == 5) {
			//printf("%f %f\n", lat, lon);

			if (n >= max) {
				max = n + 100000;

				points = realloc(points, max * sizeof(struct point));
				if (points == NULL) {
					fprintf(stderr, "fail for %d\n", max);
					exit(1);
				}
			}

			points[n].lat = lat * 1000000;
			points[n].lon = lon * 1000000;
			n++;
		}
	}

	subdivide(points, n, -90000000, -180000000, 90000000, 180000000);
	return 0;
}
