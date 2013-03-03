/*** MAIN.C ***/

#include <stdio.h>
#include <stdlib.h>		/* realloc(), qsort() */
#include <string.h>
#include <math.h>

#include "vdefs.h"

Site *nextone(void);
void readsites(void);

int triangulate, plot, debug, nsites, siteidx;
double xmin, xmax, ymin, ymax;
Site *sites;
Freelist sfl;

int main(int argc, char *argv[]) {
	int c;

	triangulate = debug = 0;
	plot = 1;
	while ((c = getopt(argc, argv, "dpt")) != EOF) {
		switch (c) {
		case 'd':
			debug = 1;
			break;
		case 't':
			triangulate = 1;
			plot = 0;
			break;
		case 'p':
			plot = 1;
			break;
		}
	}

	freeinit(&sfl, sizeof(Site));
	readsites();
	siteidx = 0;
	geominit();
	if (plot) {
		plotinit();
	}
	voronoi(nextone);
	return (0);
}

/*** sort sites on y, then x, coord ***/

int scomp(const void *vs1, const void *vs2) {
	Point *s1 = (Point *) vs1;
	Point *s2 = (Point *) vs2;

	if (s1->y < s2->y) {
		return (-1);
	}
	if (s1->y > s2->y) {
		return (1);
	}
	if (s1->x < s2->x) {
		return (-1);
	}
	if (s1->x > s2->x) {
		return (1);
	}
	return (0);
}

/*** return a single in-storage site ***/

Site *nextone(void) {
	Site *s;

	if (siteidx < nsites) {
		s = &sites[siteidx++];
		return (s);
	} else {
		return ((Site *) NULL);
	}
}


/*** read all sites, sort, and compute xmin, xmax, ymin, ymax ***/

void readsites(void) {
	int i;
	char s[2000];
	char user[2000], date[2000], time[2000];

	nsites = 0;
	sites = (Site *) myalloc(4000 * sizeof(Site));
	while (fgets(s, 2000, stdin)) {
		if (sscanf
		    (s, "%s %s %s %lf,%lf", user, date, time,
		     &sites[nsites].coord.x,
		     &sites[nsites].coord.y) != 5) {
			if (sscanf
			    (s, "%lf %lf", &sites[nsites].coord.x,
			     &sites[nsites].coord.y) != 2) {
				fprintf(stderr,
					"Don't know what to do with %s",
					s);
				continue;
			}
		}
		sites[nsites].text = strdup(s);
		sites[nsites].sitenbr = nsites;
		sites[nsites++].refcnt = 0;
		if (nsites % 4000 == 0)
			sites = (Site *)
			    realloc(sites, (nsites + 4000) * sizeof(Site));
	}

	qsort((void *) sites, nsites, sizeof(Site), scomp);

#define SMALL .000001

	int out = 0;
	for (i = 1; i < nsites; i++) {
		if (fabs(sites[i].coord.x - sites[i - 1].coord.x) > SMALL
		    || fabs(sites[i].coord.y - sites[i - 1].coord.y) >
		    SMALL) {
			sites[out] = sites[i];
			out++;
		} else {
			fprintf(stderr, "discard dup %f %f of %f %f\n",
				sites[i].coord.x, sites[i].coord.y,
				sites[i - 1].coord.x,
				sites[i - 1].coord.y);
		}
	}
	nsites = out;

	xmin = sites[0].coord.x;
	xmax = sites[0].coord.x;
	for (i = 1; i < nsites; ++i) {
		if (sites[i].coord.x < xmin) {
			xmin = sites[i].coord.x;
		}
		if (sites[i].coord.x > xmax) {
			xmax = sites[i].coord.x;
		}
	}
	ymin = sites[0].coord.y;
	ymax = sites[nsites - 1].coord.y;
}
