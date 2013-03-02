#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <limits.h>
#include <math.h>

#define BITS ((1LL << 31) * 8)
#define BYTES (BITS / 8 + 1)
#define FOOT .00000274
#define BUCKET (250 * FOOT)

#define MULT 10000000
#define LATRANGE (180LL * MULT)
#define LONRANGE (360LL * MULT)
#define LATSIZE (LATRANGE / 8 + 1)
#define LONSIZE (LONRANGE / 8 + 1)

unsigned long long hash (char *cp) {
	unsigned long long loc = 0;
	for (; *cp; cp++) {
		loc = (37 * loc + *cp) % BITS;
	}

	return loc;
}

int main(int argc, char **argv) {
	char s[2000];
	char user[2000];
	char date[2000];
	char time[2000];
	char where[2000];
	char url[2000];
	char agent[2000];
	double lat, lon;
	char userloc[2000];
	char odate[2000] = "";
	char rest[2000];

	unsigned char *field = calloc(BYTES, 1);

        unsigned char *lats, *lons;
        lats = calloc(LATSIZE, 1);
        lons = calloc(LONSIZE, 1);

	if (field == NULL || lats == NULL || lons == NULL) {
		fprintf(stderr, "Couldn't allocate memory\n");
		exit(EXIT_FAILURE);
	}

	int why_agent = 0, why_personal = 0, why_stripe = 0, why_global = 0, why_ok = 0;

	while (fgets(s, 2000, stdin)) {
		int iphone = 0;

		memset(rest, '\0', 2000);
		if (sscanf(s, "%s %s %s %s %s %s %2000c", user, date, time, where, url, agent, rest) != 7) {
			fprintf(stderr, "parse error: %s", s);
			continue;
		}

		if (strcmp(date, odate) != 0) {
			strcpy(odate, date);
			fprintf(stderr, "%s  agent %d, personal %d, stripe %d, global %d, ok %d\n",
				date, why_agent, why_personal, why_stripe, why_global, why_ok);
		}

		if (strncmp(agent, "source:Twitter_for_", 19) != 0 &&
		    strcmp(agent, "source:Instagram") != 0 &&
		    strcmp(agent, "source:foursquare") != 0) {
			why_agent++;
			continue;
		}

		if (sscanf(where, "%lf,%lf", &lat, &lon) != 2) {
			fprintf(stderr, "parse error: %s\n", where);
			continue;
		}

		if (strcmp(agent, "source:Twitter_for_iPhone") == 0 && strcmp(date, "2011-08-24") > 0) {
			iphone = 1;
		}

		long long rlat, rlon;
		long long ilat, ilon;

		if (iphone) {
			// Unique for this latitude and longitude

			ilat = ((long long) ((lat + 90) * MULT)) / 8;
			ilon = ((long long) ((lon + 180) * MULT)) / 8;

			rlat = ((long long) ((lat + 90) * MULT)) % 8;
			rlon = ((long long) ((lon + 180) * MULT)) % 8;

			if (ilat < 0 || ilat >= LATSIZE || ilon < 0 || ilon >= LONSIZE) {
				fprintf(stderr, "Out of range: %s", s);
				continue;
			}

			int latseen = lats[ilat] & (1 << rlat);
			int lonseen = lons[ilon] & (1 << rlon);

			if (latseen != 0 || lonseen != 0) {
				why_stripe++;
				continue;
			}
		}

		// Unique for this user within 250 feet

		{
			int ilat = floor(lat / BUCKET);
			double rat = cos(ilat * BUCKET * M_PI / 180);
			int ilon = floor(lon * rat / BUCKET);

			sprintf(userloc, "%s:%d,%d", user, ilat, ilon);
		}

		long long uloc = hash(userloc);
		if (field[uloc / 8] & (1 << (uloc % 8))) {
			why_personal++;
			continue;
		}

		long long gloc;
		if (!iphone) {
			// Unique for this location

			gloc = hash(where);
			if (field[gloc / 8] & (1 << (gloc % 8))) {
				why_global++;
				continue;
			}
		}

		why_ok++;
		printf("%s %s %s %s %s %s %s", user, date, time, where, url, agent, rest);

		field[uloc / 8] |= 1 << (uloc % 8);
		if (iphone) {
			lats[ilat] |= (1 << rlat);
			lons[ilon] |= (1 << rlon);
		} else {
			field[gloc / 8] |= 1 << (gloc % 8);
		}
	}

	fprintf(stderr, "%s  agent %d, personal %d, stripe %d, global %d, ok %d\n",
		date, why_agent, why_personal, why_stripe, why_global, why_ok);
	return 0;
}
