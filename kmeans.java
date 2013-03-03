/**
 * Fast K-means implementation
 *
 * Algorithm: https://www.siam.org/proceedings/datamining/2010/dm10_012_hamerlyg.pdf
 * Original C++ source: https://kvsoceanvis.googlecode.com/svn-history/r271/trunk/pcs/FastKMeansClustering.cpp
 * @author Naohisa Sakamoto
 *
 * Copyright (c) Visualization Laboratory, Kyoto University.
 * All rights reserved.
 * See http://www.viz.media.kyoto-u.ac.jp/kvs/copyright/ for details.
 *
 * Copyright 2007 Visualization Laboratory, Kyoto University.
 * All rights reserved.
 * 
 * Redistribution and  use in  source and binary  forms, with  or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 * 1.  Redistributions of  source code  must retain  the  above copyright
 * notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions  in binary form must reproduce  the above copyright
 * notice, this  list of conditions  and the following disclaimer  in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY VISUALIZATION LABORATRY, KYOTO UNIVERSITY
 * ''AS IS''  AND ANY EXPRESS  OR IMPLIED WARRANTIES, INCLUDING,  BUT NOT
 * LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR  PURPOSE ARE  DISCLAIMED. IN NO  EVENT SHALL  THE FREEBSD
 * PROJECT  OR   CONTRIBUTORS  BE   LIABLE  FOR  ANY   DIRECT,  INDIRECT,
 * INCIDENTAL, SPECIAL,  EXEMPLARY, OR CONSEQUENTIAL  DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT  OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY,  WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE  OF THIS  SOFTWARE, EVEN  IF ADVISED  OF THE  POSSIBILITY  OF SUCH
 * DAMAGE.
 * 
 * The views and conclusions  contained in the software and documentation
 * are those of the authors and should not be interpreted as representing
 * official  policies,  either  expressed  or implied,  of  Visualization
 * Laboratory, Kyoto University.
 */

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

public class kmeans {
	public kmeans() { }

	public static void main(String[] argv) {
		byte[] buf = new byte[100000];
		int used = 0;

		try {
			InputStream fi;

			if (argv.length == 0) {
				fi = System.in;
			} else {
				fi = new FileInputStream(argv[0]);
			}

			while (true) {
				if (buf.length - used < 10000) {
					byte[] buf2 = new byte[buf.length * 3/2 + 100000];
					System.arraycopy(buf, 0, buf2, 0, buf.length);
					buf = buf2;
				}

				int r = fi.read(buf, used, buf.length - used);

				if (r <= 0) {
					break;
				} else {
					used += r;
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}

		int lines = 0;
		for (int i = 0; i < used; i++) {
			if (buf[i] == '\n') {
				lines++;
			}
		}

		double[][] data = new double[lines][];

		int here = 0;
		int line = 0;
		for (int i = 0; i < used; i++) {
			if (buf[i] == '\n') {
				String[] fields = new String(buf, here, i - here).split(" ");
				String[] ao = fields[3].split(",");

				double lat = Double.parseDouble(ao[0]);
				double lon = Double.parseDouble(ao[1]);

				data[line] = new double[2];

				data[line][0] = lat;
				data[line][1] = lon;

				here = i + 1;
				line++;
			}
		}

		kmeans km = new kmeans();
		Cluster[] clusters = km.findClusters(data, 1000);

		for (int i = 0; i < clusters.length; i++) {
			for (int j = 0; j < clusters[i].center.length; j++) {
				if (j != 0) {
					System.out.print(",");
				}
				System.out.print(clusters[i].center[j]);
			}

			System.out.print(":");

			for (int j = 0; j < clusters[i].points.length; j++) {
				System.out.print(" ");

				for (int k = 0; k < clusters[i].points[j].length; k++) {
					if (k != 0) {
						System.out.print(",");
					}
					System.out.print(clusters[i].points[j][k]);
				}
			}

			System.out.print("\n");
		}
	}

	/**
	 *  @brief  Returns a copy of the row array of the table data specified by the given index.
	 *  @param  table [in] pointer to the table data
	 *  @param  i [in] row index
	 */
	double[] GetRowArray(double[][] table, int i) {
		double[] row = new double[table[0].length];
		final int ncolumns = table[0].length;

		for (int j = 0; j < ncolumns; j++) {
			row[j] = table[i][j];
		}

		return row;
	}

	/**
	 *  @brief  Returns the distance between the given points.
	 *  @param  x0 [in] point 0
	 *  @param  x1 [in] point 1
	 *  @return distance
	 */
	double GetEuclideanDistance(double[] x0, double[] x1) {
		double distance = 0.0f;
		final int nrows = x0.length;

		for (int i = 0; i < nrows; i++) {
			double diff = x1[i] - x0[i];
			distance += diff * diff;
		}

		return distance;
	}

	/**
	 *  @brief  Updates upper and lower bounds and index of the center over all centers.
	 *  @param  nclusters [in] number of clusters
	 *  @param  xi [in] data point at i-th row in the table data
	 *  @param  c [in] set of centers
	 *  @param  ai [out] index of the centers for xi
	 *  @param  ui [out] upper bound for xi
	 *  @param  li [out] lower bound for xi
	 */
	void PointAllCtrs(final int nclusters, final double[] xi, final double[][] c,
		int[] ai, double[] ui, double[] li) {
		// Algorithm 3: POINT-ALL-CTRS(x(i), c, a(i), u(i), l(i))

		int index = 0;
		double dmin = Float.MAX_VALUE;

		for (int j = 0; j < nclusters; j++) {
			final double d = GetEuclideanDistance(xi, c[j]);

			if (d < dmin) {
				dmin = d;
				index = j;
			}
		}

		ai[0] = index;
		ui[0] = GetEuclideanDistance(xi, c[ai[0]]);

		dmin = Float.MAX_VALUE;

		for (int j = 0; j < nclusters; j++) {
			if (j != ai[0]) {
				final double d = GetEuclideanDistance(xi, c[j]);
				dmin = Math.min(dmin, d);
			}
		}

		li[0] = dmin;
	}

	/**
	 *  @brief  Initializes the upper and lower bounds and the assignments.
	 *  @param  nclusters [in] number of clusters
	 *  @param  table [in] pointer to the table data
	 *  @param  c [in] set of cluster centers
	 *  @param  q [out] number of points
	 *  @param  cp [out] vector sum of all points
	 *  @param  u [out] upper bound
	 *  @param  l [out] lower bound
	 *  @param  a [out] index of the center
	 */
	void Initialize(final int nclusters, final double[][] table, final double[][] c,
		int[] q, double[][] cp, double[] u, double[] l, int[] a) {
		// Algorithm 2: INITIALIZE(c, x, q, c', u, l, a)

		for (int j = 0; j < nclusters; j++) {
			q[j] = 0;

			for (int i = 0; i < cp[j].length; i++) {
				cp[j][i] = 0;
			}
		}

		final int nrows = table.length;
		final int ncolumns = table[0].length;

		int[] ai = new int[1];
		double[] ui = new double[1];
		double[] li = new double[1];

		for (int i = 0; i < nrows; i++) {
			final double[] xi = table[i];

			ai[0] = a[i];
			li[0] = l[i];
			ui[0] = u[i];

			PointAllCtrs(nclusters, xi, c, ai, ui, li);

			a[i] = ai[0];
			u[i] = ui[0];
			l[i] = li[0];

			q[a[i]] += 1;

			for (int k = 0; k < ncolumns; k++) {
				cp[a[i]][k] += xi[k];
			}
		}
	}

	/**
	 *  @brief  Updates the center locations.
	 *  @param  cp [in] set of the vector sum of all points
	 *  @param  q [in] array of the number of points
	 *  @param  c [out] updated cluster centers
	 *  @param  p [out] array of the distance that the cluster center moved
	 */
	void MoveCenters(final double[][] cp, final int[] q, double[][] c, double[] p) {
		// Algorithm 4: MOVE-CENTERS(c', q, c, p)

		double[] cs = new double[c[0].length];

		final int nclusters = q.length;
		for (int j = 0; j < nclusters; j++) {
			for (int i = 0; i < c[j].length; i++) {
				cs[i] = c[j][i];
			}

			final int nrows = cp[j].length;
			final double qj =  q[j];

			for (int k = 0; k < nrows; k++) {
				c[j][k] = cp[j][k] / qj;
			}

			p[j] = GetEuclideanDistance(cs, c[j]);
		}
	}

	/**
	 *  @brief  Updates the upper and lower bounds.
	 *  @param  p [in] array of the distance that the cluster center moved
	 *  @param  a [in] array of index of the center
	 *  @param  u [out] upper bound
	 *  @param  l [out] lower bound
	 */
	void UpdateBounds(double[] p, int[] a, double[] u, double[] l) {
		// Algorithm 5: UPDATE-BOUNDS(p, a, u, l)

		int r = 0;
		int rp = 0;

		double pmax = Float.MIN_VALUE;
		final int nclusters = p.length;

		for (int j = 0; j < nclusters; j++) {
			if (p[j] > pmax) {
				pmax = p[j];
				r = j;
			}
		}

		pmax = Float.MIN_VALUE;
		for (int j = 0; j < nclusters; j++) {
			if (j != r) {
				if (p[j] > pmax) {
					pmax = p[j];
					rp = j;
				}
			}
		}

		final int nrows = u.length;
		for (int i = 0; i < nrows; i++) {
			u[i] += p[a[i]];

			if (r == a[i]) {
				l[i] -= p[rp];
			} else {
				l[i] -= p[r];
			}
		}
	}

	/**
	 *  @brief  Updates the number of points specified by the given index.
	 *  @param  m [in] index of the center
	 *  @param  a [in] array of index of the center
	 *  @param  q [in/out] updated the array of the number of points
	 */
	void Update(final int m, final int[] a, int[] q) {
		int counter = 0;
		final int nrows = a.length;

		for (int i = 0; i < nrows; i++) {
			if (a[i] == m) {
				counter++;
			}
		}

		q[m] = counter;
	}

	/**
	 *  @brief  Updates the vector sum of all points specified by the given index.
	 *  @param  m [in] index of the center
	 *  @param  table [in] pointer to the table data
	 *  @param  a [in] array of index of the center
	 *  @param  cp [out] set of the vector sum of all points
	 */
	void Update(final int m, final double[][] table, final int[] a, double[][] cp) {
		final int nrows = a.length;
		final int ncolumns = table[0].length;

		for (int i = 0; i < cp[m].length; i++) {
			cp[m][i] = 0;
		}
		for (int i = 0; i < nrows; i++) {
			if (a[i] == m) {
				for (int k = 0; k < ncolumns; k++) {
					cp[m][k] += table[i][k];
				}
			}
		}
	}

	public static class Cluster {
		public double[] center;
		public double[][] points;
	}

	/**
	 *  @brief  Executes Hamerly's k-means clustering.
	 *  @param  object [in] pointer to the table object
	 *  @return pointer to the clustered table object
	 */
	public Cluster[] findClusters(final double[][] object, int nclusters) {
		return findClusters(object, nclusters, 100, 1e-6);
	}

	/**
	 *  @brief  Executes Hamerly's k-means clustering.
	 *  @param  object [in] pointer to the table object
	 *  @return pointer to the clustered table object
	 */
	public Cluster[] findClusters(final double[][] object, int nclusters,
				      int maxIterations, double tolerance) {
		// Input table object.
		final double[][] table = object;
		final int nrows = table.length;
		final int ncolumns = table[0].length;

		if (nclusters > nrows) {
			nclusters = nrows;
		}

		// Parameters that relate to cluster centers.
		/*   c:  cluster center
		 *   cp: vector sum of all points in the cluster
		 *   q:  number of points assigned to the cluster
		 *   p:  distance that c last moved
		 *   s:  distance from c to its closest other center
		 */
		double[][] c = new double[nclusters][];
		double[][] cp = new double[nclusters][];
		int[] q = new int[nclusters];
		double[] p = new double[nclusters];
		double[] s = new double[nclusters];

		for (int j = 0; j < nclusters; j++) {
			c[j] = new double[ncolumns];
			cp[j] = new double[ncolumns];
		}

		// Parameters that relate to data points.
		/*   a:  index of the center to which the data point x is assigned
		 *   u:  upper bound on the distance between the data point x and
		 *       its assigned center c(a)
		 *   l:  lower bound on the distance between the data point x and
		 *       its second closest center (the closest center to the data
		 *       point that is not c(a))
		 */
		int[] a = new int[nrows];
		double[] u = new double[nrows];
		double[] l = new double[nrows];

		// Assign initial centers.
		for (int j = 0; j < nclusters; j++) {
			final int index = j; // (int) (Math.random() * nrows);
			c[j] = GetRowArray(table, index);
		}

		// Initialize.
		Initialize(nclusters, table, c, q, cp, u, l, a);

		// Cluster IDs.
		int[] IDs;

		// Clustering.
		boolean converged = false;
		int counter = 0;

		while (!converged) {
			// Update s.
			for (int j = 0; j < nclusters; j++) {
				double dmin = Float.MAX_VALUE;
				for (int jp = 0; jp < nclusters; jp++) {
					if (jp != j) {
						final double d = GetEuclideanDistance(c[jp], c[j]);
						dmin = Math.min(dmin, d);
					}
				}
				s[j] = dmin;
			}

			int[] ai = new int[1];
			double[] ui = new double[1];
			double[] li = new double[1];

			for (int i = 0; i < nrows; i++) {
				final double m = Math.max(s[a[i]] * 0.5f, l[i]);
				if (u[i] > m) { // First bound test.
					// Tighten upper bound.
					final double[] xi = table[i];
					u[i] = GetEuclideanDistance(xi, c[a[i]]);

					if (u[i] > m) { // Second bound test.
						final int ap = a[i];

						ai[0] = a[i];
						ui[0] = u[i];
						li[0] = l[i];
						PointAllCtrs(nclusters, xi, c, ai, ui, li);
						a[i] = ai[0];
						u[i] = ui[0];
						l[i] = li[0];

						if (ap != a[i]) {
							Update(ap, a, q);
							Update(a[i], a, q);
							Update(ap, table, a, cp);
							Update(a[i], table, a, cp);
						}
					}
				}
			}

			MoveCenters(cp, q, c, p);
			UpdateBounds(p, a, u, l);

			// Update cluster IDs.
			IDs = a;

			// Convergence test.
			converged = true;
			double bad = 0;
			for (int j = 0; j < nclusters; j++) {
				if (!(p[j] < tolerance || Double.isNaN(p[j]))) {
					bad = p[j];
					converged = false;
					break;
				}
			}

			if (counter++ > maxIterations) {
				break;
			}

			System.err.println("Trying again: " + bad);
		}

		Cluster[] ret = new Cluster[nclusters];
		int[] count = new int[nclusters];

		for (int i = 0; i < nrows; i++) {
			count[a[i]]++;
		}

		for (int i = 0; i < nclusters; i++) {
			ret[i] = new Cluster();
			ret[i].center = c[i];
			ret[i].points = new double[count[i]][];
			count[i] = 0;
		}

		for (int i = 0; i < nrows; i++) {
			ret[a[i]].points[count[a[i]]] = table[i];
			count[a[i]]++;
		}

		return ret;
	}
}
