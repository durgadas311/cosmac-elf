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
				monSize = fi.available();
				if (monMask <= 32) {
					monSize = 32;
				} else if (monMask <= 256) {
					monSize = 256;
				} else {
					System.err.format("ROM size unexpected\n");
				}
				monMask = monSize - 1;
				mon = new byte[monSize];
				fi.read(mon);
				fi.close();
				//System.err.format("PROM loaded %d bytes\n", monSize);
			} catch (Exception ee) {
				ee.printStackTrace();
				System.exit(1);
			}
		}
	}
}
