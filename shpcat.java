import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.util.ArrayList;
import java.io.*;

public class shpcat {
	static boolean json = false;

	public static void main(String[] arg) {
		try {
			for (int i = 0; i < arg.length; i++) {
				if (arg[i].equals("-j")) {
					json = true;
				} else {
					ZipFile zf = new ZipFile(arg[i]);
					extract(zf);
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	public static void extract(ZipFile zf) throws IOException {
		Enumeration<? extends ZipEntry> e = zf.entries();
		ZipEntry shp = null;
		ZipEntry dbf = null;

		while (e.hasMoreElements()) {
			ZipEntry ze = e.nextElement();

			if (ze.getName().endsWith(".shp")) {
				shp = ze;
			}
			if (ze.getName().endsWith(".dbf")) {
				dbf = ze;
			}
		}

		if (shp == null) {
			throw new RuntimeException("No .shp file in " + zf);
		}
		if (dbf == null) {
			throw new RuntimeException("No .dbf file in " + zf);
		}

		extract(zf.getInputStream(shp), zf.getInputStream(dbf));
	}

	public static void read(InputStream stream, byte[] ba, int len) 
			throws IOException {
		int off = 0;

		while (off < len) {
			int nread = stream.read(ba, off, len - off);

			if (nread <= 0) {
				throw new RuntimeException("short read");
			}

			off += nread;
		}
	}

	public static int read32le(byte[] ba, int off) {
		return ((ba[off] & 0xFF)) |
			((ba[off + 1] & 0xFF) << 8) |
			((ba[off + 2] & 0xFF) << 16) |
			((ba[off + 3] & 0xFF) << 24);
	}

	public static int read16le(byte[] ba, int off) {
		return ((ba[off] & 0xFF)) |
			((ba[off + 1] & 0xFF) << 8);
	}

	public static long read64le(byte[] ba, int off) {
		return read32le(ba, off) |
			(((long) read32le(ba, off + 4)) << 32);
	}

	public static int read32be(byte[] ba, int off) {
		return ((ba[off] & 0xFF) << 24) |
			((ba[off + 1] & 0xFF) << 16) |
			((ba[off + 2] & 0xFF) << 8) |
			((ba[off + 3] & 0xFF));
	}

	public static String toDouble(byte[] ba, int off) {
	    long l = (((long) (ba[off + 0] & 0xFF)) << 0) |
		     (((long) (ba[off + 1] & 0xFF)) << 8) |
		     (((long) (ba[off + 2] & 0xFF)) << 16) |
		     (((long) (ba[off + 3] & 0xFF)) << 24) |
		     (((long) (ba[off + 4] & 0xFF)) << 32) |
		     (((long) (ba[off + 5] & 0xFF)) << 40) |
		     (((long) (ba[off + 6] & 0xFF)) << 48) |
		     (((long) (ba[off + 7] & 0xFF)) << 56);

	    double d = Double.longBitsToDouble(l);
	    return String.format("%.6f", d);
	}

	public static void jquote(CharSequence cs) {
		System.out.print('"');

		int i;
		for (i = 0; i < cs.length(); i++) {
			char c = cs.charAt(i);

			if (c == '\\' || c == '"') {
				System.out.print("\\" + c);
			} else {
				/* XXX control characters */
				System.out.print(c);
			}
		}

		System.out.print('"');
	}

	public static void extract(InputStream shp, InputStream dbf)
			throws IOException {
		byte[] shpheader = new byte[100];
		read(shp, shpheader, 100);

		int magic = read32be(shpheader, 0);
		int flen = 2 * read32be(shpheader, 24) - 100;
		int version = read32le(shpheader, 28);

		if (magic != 9994) {
			throw new RuntimeException("Bad magic " + magic);
		}
		if (version != 1000) {
			throw new RuntimeException("Bad version " + version);
		}

		if (!json) {
			System.out.print("bbox " + toDouble(shpheader, 36) + "," + toDouble(shpheader, 44) +
					 " " + toDouble(shpheader, 52) + "," + toDouble(shpheader, 60) + "\n");
		}

		if (json) {
			System.out.print("{\n");
			System.out.print("\"type\": \"FeatureCollection\",\n");
			System.out.print("\"features\": [\n");
		}

		byte[] dbfheader = new byte[32];
		read(dbf, dbfheader, 32);

		int dbheaderlen = read16le(dbfheader, 8);
		int dbreclen = read16le(dbfheader, 10);

		byte[] dbcolumns = new byte[dbheaderlen - 32];
		read(dbf, dbcolumns, dbcolumns.length);

		ArrayList<String> titles = new ArrayList<String>();

		int[] dbflen = new int[dbcolumns.length / 32];
		for (int i = 0; i < dbflen.length; i++) {
			int start = i * 32;
			int end;
			for (end = start; end < start + 10 && dbcolumns[end] != '\0'; end++)
				;

			String column = new String(dbcolumns, start, end - start);
			titles.add(column);

			if (!json) {
				System.out.print(column + "|");
			}

			dbflen[i] = dbcolumns[32 * i + 16] & 0xff;
		}

		if (!json) {
			System.out.println();
		}

		//byte[] backlink = new byte[264];
		//read(dbf, backlink, backlink.length);

		{
			byte[] db = new byte[dbreclen];
			byte[] header = new byte[8];
			boolean first = true;

			while (flen > 0) {
				if (json) {
					if (!first) {
						System.out.println(",");
					}
				}
				first = false;

				read(dbf, db, dbreclen);

				if (json) {
					System.out.print("{ \"type\": \"Feature\", \"properties\": { ");
				}

				int here = 1;
				for (int i = 0; i < dbflen.length; i++) {
					String s = new String(db, here, dbflen[i]);

					if (json) {
						int l = s.length();
						for (int m = 0; m < s.length(); m++) {
							if (s.charAt(m) != ' ') {
								l = m + 1;
							}
						}
						s = s.substring(0, l);

						if (i > 0) {
							System.out.print(", ");
						}

						jquote(titles.get(i));
						System.out.print(": ");
						jquote(s);
					} else {
						s.replace('|', '!');
						System.out.print(s + "|");
					}

					here += dbflen[i];
				}

				if (json) {
					System.out.print(" }, \"geometry\": { ");
				}

				read(shp, header, 8);
				flen -= 8;

				int recno = read32be(header, 0);
				int len = 2 * read32be(header, 4);

				byte[] content = new byte[len];
				read(shp, content, len);
				flen -= len;

				int type = read32le(content, 0);
				System.out.print(decode(type, content));

				if (json) {
					System.out.print("} }\n");
				} else {
					System.out.print("\n");
				}
			}
		}

		if (json) {
			System.out.println("] }");
		}
	}

	public static String decode(int type, byte[] content) {
		switch (type) {
		case 0:
			return "null";

		case 1:
			if (json) {
				return "\"type\": \"Point\", \"coordinates\": [ " + toDouble(content, 4) + "," + toDouble(content, 12) + "]";
			} else {
				return "point " + toDouble(content, 4) + "," + toDouble(content, 12);
			}

		case 3: {
			StringBuilder sb;

			if (json) {
				sb = new StringBuilder("\"type\": \"LineString\", \"coordinates\": [ ");
			} else {
				sb = new StringBuilder("polyline ");
			}

			int npart = read32le(content, 36);
			int npoint = read32le(content, 40);

			for (int i = 0; i < npoint; i++) {
				int off = 44 + 4 * npart + 16 * i;

				if (json) {
					if (i != 0) {
						sb.append(", ");
					}
					sb.append("[ ");
				}

				sb.append(toDouble(content, off) + "," +
					  toDouble(content, off + 8) + " ");

				if (json) {
					sb.append("]");
				}
			}

			if (json) {
				sb.append("]");
			}

			return sb.toString();
		}

		case 5: {
			StringBuilder sb;

			if (json) {
				sb = new StringBuilder("\"type\": \"Polygon\", \"coordinates\": [ ");
			} else {
				sb = new StringBuilder("polygon ");
			}

			int npart = read32le(content, 36);
			int npoint = read32le(content, 40);

			for (int i = 0; i < npart; i++) {
				int off = read32le(content, 44 + 4 * i);
				int end;

				if (i == npart - 1) {
					end = npoint;
				} else {
					end = read32le(content, 44 + 4 * (i + 1));
				}

				if (json) {
					if (i != 0) {
						sb.append(", ");
					}
					sb.append("[ ");
				}

				for (int j = off; j < end; j++) {
					if (json) {
						if (j != off) {
							sb.append(", ");
						}
						sb.append("[ ");
					}

					sb.append(toDouble(content, 44 + 4 * npart + 16 * j) + "," +
						  toDouble(content, 52 + 4 * npart + 16 * j) + " ");

					if (json) {
						sb.append("]");
					}
				}

				if (json) {
					sb.append("]");
				} else {
					sb.append("; ");
				}
			}

			if (json) {
				sb.append("]");
			}

			return sb.toString();
		}

		case 8: {
			StringBuilder sb;

			if (json) {
				sb = new StringBuilder("\"type\": \"MultiPoint\", \"coordinates\": [ ");
			} else {
				sb = new StringBuilder("multipoint ");
			}

			int npoint = read32le(content, 36);
			for (int i = 0; i < npoint; i++) {
				if (json) {
					if (i != 0) {
						sb.append(", ");
					}

					sb.append("[ ");
				}

				sb.append(toDouble(content, 40 + 16 * i) + "," +
					  toDouble(content, 48 + 16 * i) + " ");

				if (json) {
					sb.append("] ");
				}
			}

			if (json) {
				sb.append("]");
			}

			return sb.toString();
		}

		case 23: {
			StringBuilder sb = new StringBuilder("polylinem ");

			int npart = read32le(content, 36);
			int npoint = read32le(content, 40);
			for (int i = 0; i < npart; i++) {
				int off = read32le(content, 44 + 4 * i);
				int end;

				if (i == npart - 1) {
					end = npoint;
				} else {
					end = read32le(content, 44 + 4 * (i + 1));
				}

				for (int j = off; j < end; j++) {
					sb.append(toDouble(content, 44 + 4 * npart + 16 * j) + "," +
						  toDouble(content, 52 + 4 * npart + 16 * j) + " ");
				}

				sb.append("; ");
			}

			return sb.toString();
		}
			

		default:
			return "unknown " + type;
		}
	}
}
