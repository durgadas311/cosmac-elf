// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

public interface DMAController {
	boolean isActive(boolean in);
	int readDataBus();
	void writeDataBus(int val);
}
