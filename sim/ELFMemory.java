// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;

// TODO: extends ELFRoms...
public class ELFMemory implements Memory {
	private byte[] mem;
	private int mask;
	private boolean rom = false;

	public ELFMemory(Properties props) {
		//super(props, gpio, intr);
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
		//Arrays.fill(mem, (byte)0x30);
	}

	public int read(boolean rom, int address) {
		address &= mask;
		if (rom && false) {
			// read ROM instead
		}
		return mem[address] & 0xff;
	}
	public int read(int address) {
		return read(rom, address);
	}
	public void write(int address, int value) {
		address &= mask;
		if (rom && false) {
			// ROM - no write to RAM...
			return;
		}
		mem[address] = (byte)value;
	}

	public void reset() {}

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
