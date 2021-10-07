// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Properties;

public class ELFRoms {
	protected byte[] mon;
	protected int monSize = 0;
	protected int monMask = 0;

	public ELFRoms(Properties props) {
		String s = props.getProperty("prom");
		if (s != null) {
			try {
				InputStream fi = new FileInputStream(s);
				monSize = fi.available() & 0xffff;
				int x = 32;
				while (x < monSize) x <<= 1;
				monSize = x;
				monMask = monSize - 1;
				mon = new byte[monSize];
				fi.read(mon);
				fi.close();
				System.err.format("PROM is %d bytes loaded from %s\n",
						monSize, s);
			} catch (Exception ee) {
				ee.printStackTrace();
				System.exit(1);
			}
		}
	}
}
