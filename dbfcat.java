import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.io.*;

public class dbfcat {
	public static void main(String[] arg) {
		try {
			for (int i = 0; i < arg.length; i++) {
				if (arg[i].endsWith(".zip")) {
					ZipFile zf = new ZipFile(arg[i]);
					extract(zf);
				} else {
					extract(new FileInputStream(arg[i]));
				}
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.exit(1);
		}
	}

	public static void extract(ZipFile zf) throws IOException {
		Enumeration<? extends ZipEntry> e = zf.entries();
		ZipEntry dbf = null;

		while (e.hasMoreElements()) {
			ZipEntry ze = e.nextElement();

			if (ze.getName().endsWith(".dbf")) {
				dbf = ze;
			}
		}

		if (dbf == null) {
			throw new RuntimeException("No .dbf file in " + zf);
		}

		extract(zf.getInputStream(dbf));
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

	public static void extract(InputStream dbf)
			throws IOException {
		byte[] dbfheader = new byte[32];
		read(dbf, dbfheader, 32);

		int flen = read32le(dbfheader, 4);
		int dbheaderlen = read16le(dbfheader, 8);
		int dbreclen = read16le(dbfheader, 10);

		byte[] dbcolumns = new byte[dbheaderlen - 32];
		read(dbf, dbcolumns, dbcolumns.length);

		int[] dbflen = new int[dbcolumns.length / 32];
		for (int i = 0; i < dbflen.length; i++) {
			int start = i * 32;
			int end;
			for (end = start; end < start + 10 && dbcolumns[end] != '\0'; end++)
				;
			System.out.print(new String(dbcolumns, start, end - start) + "|");
			dbflen[i] = dbcolumns[32 * i + 16] & 0xff;
		}

		System.out.println();

		//byte[] backlink = new byte[264];
		//read(dbf, backlink, backlink.length);

		{
			byte[] db = new byte[dbreclen];
			byte[] header = new byte[8];

			while (flen > 0) {
				read(dbf, db, dbreclen);

				int here = 1;
				for (int i = 0; i < dbflen.length; i++) {
					String s = new String(db, here, dbflen[i]);
					s.replace('|', '!');
					System.out.print(s + "|");

					here += dbflen[i];
				}

				System.out.println();
				flen--;
			}
		}
	}

	public static String decode(int type, byte[] content) {
		switch (type) {
		case 0:
			return "null";

		case 1:
			return "point " + toDouble(content, 4) + "," +
				toDouble(content, 12);

		case 3: {
			StringBuilder sb = new StringBuilder("polyline ");

			int npart = read32le(content, 36);
			int npoint = read32le(content, 40);

			for (int i = 0; i < npoint; i++) {
				int off = 44 + 4 * npart + 16 * i;

				sb.append(toDouble(content, off) + "," +
					  toDouble(content, off + 8) + " ");
			}

			return sb.toString();
		}

		case 5: {
			StringBuilder sb = new StringBuilder("polygon ");

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

		case 8: {
			StringBuilder sb = new StringBuilder("multipoint ");

			int npoint = read32le(content, 36);
			for (int i = 0; i < npoint; i++) {
				sb.append(toDouble(content, 40 + 16 * i) + "," +
					  toDouble(content, 48 + 16 * i) + " ");
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
