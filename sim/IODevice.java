// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

public interface IODevice {
	void reset();
	int getBaseAddress();
	int getMask();
	int in(int port);
	void out(int port, int value);
	String getDeviceName();
	String dumpDebug();
}
