// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;

// TODO: extends ELFRoms...
public class ELFMemory extends ELFRoms implements Memory {
	private byte[] mem;
	private int mask;
	private boolean rom = false;

	public ELFMemory(Properties props) {
		super(props);
		int ramsize = 256;
		String s = props.getProperty("ram");
		if (s != null) {
			int mult = 1;
			if (s.endsWith("k") || s.endsWith("K")) {
				mult = 1024;
				s = s.substring(0, s.length() - 1);
			}
			ramsize = Integer.valueOf(s) * mult;
			if ((ramsize & (ramsize - 1)) != 0) {
				int n = 32 - Integer.numberOfLeadingZeros(ramsize);
				if (n < 9) n = 9;
				if (n > 17) n = 17;
				ramsize = (1 << n);	// next larger
				System.err.format(
					"RAM size is not power-of-two, using %d\n",
					ramsize);
			}
		}
		mem = new byte[ramsize];
		mask = ramsize - 1;	// only works for powers of two
		// RAM must be >= ROM size...
		//Arrays.fill(mem, (byte)0x30);
	}

	public int read(boolean rom, int address) {
		address &= mask;
		if (rom && address < monSize) {
			// read ROM instead
			return mon[address] & 0xff;
		}
		return mem[address] & 0xff;
	}
	public int read(int address) {
		return read(rom, address);
	}
	public void write(int address, int value) {
		address &= mask;
		if (rom && address < monSize) {
			// ROM - no write to RAM...
			return;
		}
		mem[address] = (byte)value;
	}

	public void reset() {}

	public void setROM(boolean ena) {
		if (monSize > 0) rom = ena;
	}

	public void copy() {
		if (monSize > 0) {
			System.arraycopy(mon, 0, mem, 0, monSize);
		}
	}

	public void load(String file, int adr) {
		try {
			InputStream core = new FileInputStream(file);
			core.read(mem, adr, mem.length - adr);
			core.close();
		} catch (Exception ee) {
			ELFOperator.error(null, "Load Prog", ee.getMessage());
		}
	}

	public void dumpCore(String file) {
		try {
			OutputStream core = new FileOutputStream(file);
			core.write(mem);
			core.close();
		} catch (Exception ee) {
			ELFOperator.error(null, "Core Dump", ee.getMessage());
		}
	}
}
