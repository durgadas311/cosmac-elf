// Copyright (c) 2016 Douglas Miller <durgadas311@gmail.com>

public interface Interruptor {
	static final int SIMPLE = 1;	// I/O Decoder
	static final int STRICT = 2;	// I/O Decoder
	static final int EXTENDED = 3;	// I/O Decoder
	static final int ELF = 100;	// Model
	static final int ELF2 = 101;	// Model
	int getModel();
	int IODecoder();
	int registerINT();
	void raiseINT(int src);
	void lowerINT(int src);
	void raiseDMA_IN(int src);
	void lowerDMA_IN(int src);
	void raiseDMA_OUT(int src);
	void lowerDMA_OUT(int src);
	void setEF(int src, int ef, boolean on);
	void setSwitch(int sw, boolean on);
	void blockInts(int mask);
	void unblockInts(int mask);
	void addClockListener(ClockListener lstn);
	void addTimeListener(TimeListener lstn);
	void addIntrController(InterruptController ctrl);
	void addDMAController(DMAController ctrl);
	void addQListener(QListener lstn);
	int getSpeed();	// CPU raw clock speed (ticks/sec for addTicks())
	void waitCPU();
	boolean isTracing();
	void startTracing(int cy);
	void stopTracing();
}
