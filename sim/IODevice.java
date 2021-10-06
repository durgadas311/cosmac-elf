// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

public interface IODevice {
	static final int IN = 0b01;
	static final int OUT = 0b10;
	static final int IN_OUT = IN | OUT;
	void reset();
	int getBaseAddress();
	int getMask();
	int getDevType();	// IN, OUT, IN+OUT
	int in(int port);
	void out(int port, int value);
	String getDeviceName();
	String dumpDebug();
}
