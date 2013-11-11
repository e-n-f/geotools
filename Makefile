all: cleanse hachure pnpoly subdivide-world

cleanse: cleanse.c
	cc -g -Wall -O3 -o cleanse cleanse.c -lm

hachure: hachure.c
	cc -g -Wall -O3 -o hachure hachure.c -lm

pnpoly: pnpoly.c
	cc -g -Wall -O3 -o pnpoly pnpoly.c -lm

subdivide-world: subdivide-world.c
	cc -g -Wall -O3 -o subdivide-world subdivide-world.c

