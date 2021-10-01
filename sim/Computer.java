// Copyright 2021 Douglas Miller <durgadas311@gmail.com>

public interface Computer {
	// Memory access
	int peek8(int address);
	void poke8(int address, int value);

	// I/O access
	int inPort(int port);
	void outPort(int port, int value);

	// DMA
	int dmaIn();
	void dmaOut(int val);
}
