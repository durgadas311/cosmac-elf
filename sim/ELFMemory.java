// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;

// TODO: extends ELFRoms...
public class ELFMemory implements Memory {
	private byte[] mem;
	private int top;
	private int bot;
	private boolean rom;

	public ELFMemory(Properties props) {
		//super(props, gpio, intr);	// cannot be ROMX
//		int memsiz = 4096 * high;
//		if (memsiz <= 0) {
//			memsiz = 4096;
//		}
//		bot = 4096 * low;
//		top = memsiz;
		bot = 0;
		top = 1024;
		mem = new byte[top];
		//Arrays.fill(mem, (byte)0x30);
	}

	public int read(boolean rom, int address) {
		address &= 0xffff; // necessary?
		if (address >= top || address < bot) {
			return 0;
		}
		return mem[address] & 0xff;
	}
	public int read(int address) {
		return read(rom, address);
	}
	public void write(int address, int value) {
		address &= 0xffff; // necessary?
		if (address >= top || address < bot) {
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
