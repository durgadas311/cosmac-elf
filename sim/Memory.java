// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

public interface Memory {
	int read(boolean rom, int address); // debugger interface
	int read(int address);
	void write(int address, int value);
	void reset();
	void setROM(boolean ena);
	void copy();
	void load(String file, int adr);
	void dumpCore(String file);
}
