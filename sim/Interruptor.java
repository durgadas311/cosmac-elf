// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

public interface Interruptor {
	int registerINT();
	void raiseINT(int src);
	void lowerINT(int src);
	void raiseDMA_IN(int src);
	void lowerDMA_IN(int src);
	void raiseDMA_OUT(int src);
	void lowerDMA_OUT(int src);
	void blockInts(int mask);
	void unblockInts(int mask);
	void addClockListener(ClockListener lstn);
	void addTimeListener(TimeListener lstn);
	void addIntrController(InterruptController ctrl);
	void addDMAController(DMAController ctrl);
	void waitCPU();
	boolean isTracing();
	void startTracing(int cy);
	void stopTracing();
}
