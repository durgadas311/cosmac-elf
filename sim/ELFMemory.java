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
			// TODO: parse "1K" etc.
			ramsize = Integer.valueOf(s);
			if ((ramsize & (ramsize - 1)) != 0) {
				System.err.format("RAM size is not power-of-two\n");
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

	public void dumpCore(String file) {
		try {
			OutputStream core = new FileOutputStream(file);
			core.write(mem);
			core.close();
		} catch (Exception ee) {
			//ELFOperator.error(null, "Core Dump", ee.getMessage());
			ee.printStackTrace();
		}
	}
}
