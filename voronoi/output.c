
/*** OUTPUT.C ***/


#include <stdio.h>
#include <math.h>

#include "vdefs.h"

extern int triangulate, plot, debug;
extern double ymax, ymin, xmax, xmin;

double pxmin, pxmax, pymin, pymax, cradius;

void openpl(void) {
}

#pragma argsused
void line(double ax, double ay, double bx, double by) {
	printf("%.6f %.6f moveto %.6f %.6f lineto stroke\n", ax, ay, bx,
	       by);
}

#pragma argsused
void circle(double ax, double ay, double radius) {
}

#pragma argsused
void range(double pxmin, double pxmax, double pymin, double pymax) {
}

void out_bisector(Edge * e) {
	if (triangulate && plot && !debug) {
		line(e->reg[0]->coord.x, e->reg[0]->coord.y,
		     e->reg[1]->coord.x, e->reg[1]->coord.y);
	}
	if (!triangulate && !plot && !debug) {
		printf("l %f %f %f\n", e->a, e->b, e->c);
	}
	if (debug) {
		printf("line(%d) %gx+%gy=%g, bisecting %d %d\n",
		       e->edgenbr, e->a, e->b, e->c, e->reg[le]->sitenbr,
		       e->reg[re]->sitenbr);
	}
}

void out_ep(Edge * e) {
	if (!triangulate && plot) {
#if 0
		printf("ep: endpoints: ");
		printf(" %d ",
		       e->ep[le] !=
		       (Site *) NULL ? e->ep[le]->sitenbr : -1);
		printf("%d",
		       e->ep[re] !=
		       (Site *) NULL ? e->ep[re]->sitenbr : -1);
		printf("\n");
		printf("ep: regions: ");
		printf(" %d ",
		       e->reg[le] !=
		       (Site *) NULL ? e->reg[le]->sitenbr : -1);
		printf("%d ",
		       e->reg[re] !=
		       (Site *) NULL ? e->reg[re]->sitenbr : -1);
		printf(" region points: ");
		printf("%f %f %f %f\n", e->reg[0]->coord.x,
		       e->reg[0]->coord.y, e->reg[1]->coord.x,
		       e->reg[1]->coord.y);
#endif


		clip_line(e);

	}
	if (!triangulate && !plot) {
		printf("e %d", e->edgenbr);
		printf(" %d ",
		       e->ep[le] !=
		       (Site *) NULL ? e->ep[le]->sitenbr : -1);
		printf("%d\n",
		       e->ep[re] !=
		       (Site *) NULL ? e->ep[re]->sitenbr : -1);
	}
}

void out_vertex(Site * v) {
	if (!triangulate && !plot && !debug) {
		printf("v %f %f\n", v->coord.x, v->coord.y);
	}
	if (debug) {
		printf("vertex(%d) at %f %f\n", v->sitenbr, v->coord.x,
		       v->coord.y);
	}
}

void out_site(Site * s) {
	if (!triangulate && plot && !debug) {
		circle(s->coord.x, s->coord.y, cradius);
	}
	if (!triangulate && !plot && !debug) {
		printf("s %f %f\n", s->coord.x, s->coord.y);
	}
	if (debug) {
		printf("site (%d) at %f %f\n", s->sitenbr, s->coord.x,
		       s->coord.y);
	}
}

void out_triple(Site * s1, Site * s2, Site * s3) {
	if (triangulate && !plot && !debug) {
#if 0
		printf("%d %d %d\n", s1->sitenbr, s2->sitenbr,
		       s3->sitenbr);
#endif
		printf("%d %f,%f %d %f,%f %d %f,%f\n",
		       s1->sitenbr, s1->coord.x, s1->coord.y,
		       s2->sitenbr, s2->coord.x, s2->coord.y,
		       s3->sitenbr, s3->coord.x, s3->coord.y);
	}
	if (debug) {
		printf("circle through left=%d right=%d bottom=%d\n",
		       s1->sitenbr, s2->sitenbr, s3->sitenbr);
	}
}

void plotinit(void) {
	double dx, dy, d;

	dy = ymax - ymin;
	dx = xmax - xmin;
	d = (dx > dy ? dx : dy) * 1.1;
	pxmin = xmin - (d - dx) / 2.0;
	pxmax = xmax + (d - dx) / 2.0;
	pymin = ymin - (d - dy) / 2.0;
	pymax = ymax + (d - dy) / 2.0;
	cradius = (pxmax - pxmin) / 350.0;
	openpl();
	range(pxmin, pymin, pxmax, pymax);
}

void clip_line(Edge * e) {
	Site *s1, *s2;
	double x1, x2, y1, y2;

	if (e->a == 1.0 && e->b >= 0.0) {
		s1 = e->ep[1];
		s2 = e->ep[0];
	} else {
		s1 = e->ep[0];
		s2 = e->ep[1];
	}
	if (e->a == 1.0) {
		y1 = pymin;
		if (s1 != (Site *) NULL && s1->coord.y > pymin) {
			y1 = s1->coord.y;
		}
		if (y1 > pymax) {
			return;
		}
		x1 = e->c - e->b * y1;
		y2 = pymax;
		if (s2 != (Site *) NULL && s2->coord.y < pymax) {
			y2 = s2->coord.y;
		}
		if (y2 < pymin) {
			return;
		}
		x2 = e->c - e->b * y2;
		if (((x1 > pxmax) && (x2 > pxmax))
		    || ((x1 < pxmin) && (x2 < pxmin))) {
			return;
		}
		if (x1 > pxmax) {
			x1 = pxmax;
			y1 = (e->c - x1) / e->b;
		}
		if (x1 < pxmin) {
			x1 = pxmin;
			y1 = (e->c - x1) / e->b;
		}
		if (x2 > pxmax) {
			x2 = pxmax;
			y2 = (e->c - x2) / e->b;
		}
		if (x2 < pxmin) {
			x2 = pxmin;
			y2 = (e->c - x2) / e->b;
		}
	} else {
		x1 = pxmin;
		if (s1 != (Site *) NULL && s1->coord.x > pxmin) {
			x1 = s1->coord.x;
		}
		if (x1 > pxmax) {
			return;
		}
		y1 = e->c - e->a * x1;
		x2 = pxmax;
		if (s2 != (Site *) NULL && s2->coord.x < pxmax) {
			x2 = s2->coord.x;
		}
		if (x2 < pxmin) {
			return;
		}
		y2 = e->c - e->a * x2;
		if (((y1 > pymax) && (y2 > pymax))
		    || ((y1 < pymin) && (y2 < pymin))) {
			return;
		}
		if (y1 > pymax) {
			y1 = pymax;
			x1 = (e->c - y1) / e->a;
		}
		if (y1 < pymin) {
			y1 = pymin;
			x1 = (e->c - y1) / e->a;
		}
		if (y2 > pymax) {
			y2 = pymax;
			x2 = (e->c - y2) / e->a;
		}
		if (y2 < pymin) {
			y2 = pymin;
			x2 = (e->c - y2) / e->a;
		}
	}

	double a, b, c, s, area;
	Site os1, os2;

	os1.coord.x = x1;
	os1.coord.y = y1;
	os2.coord.x = x2;
	os2.coord.y = y2;

	a = dist(e->reg[0], &os1);
	b = dist(e->reg[0], &os2);
	c = dist(&os1, &os2);
	s = (a + b + c) / 2;
	area = sqrt(s * (s - a) * (s - b) * (s - c));
	//printf("%f %f %f -> ", a, b, c);

	printf("%f,%f ", e->reg[0]->coord.x, e->reg[0]->coord.y);
	printf("%f,%f %f,%f ", x1, y1, x2, y2);
	printf("%f ", area);
	printf("%s", e->reg[0]->text);

	a = dist(e->reg[1], &os1);
	b = dist(e->reg[1], &os2);
	c = dist(&os1, &os2);
	s = (a + b + c) / 2;
	area = sqrt(s * (s - a) * (s - b) * (s - c));
	//printf("%f %f %f -> ", a, b, c);

	printf("%f,%f ", e->reg[1]->coord.x, e->reg[1]->coord.y);
	printf("%f,%f %f,%f ", x1, y1, x2, y2);
	printf("%f ", area);
	printf("%s", e->reg[1]->text);
}
