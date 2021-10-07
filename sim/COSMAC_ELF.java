// Copyright 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.io.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Properties;
import javax.swing.*;

public class COSMAC_ELF implements Computer, ELFCommander, Interruptor, Runnable {
	private CDP1802 cpu;
	private long clock;
	private Vector<IODevice> devs;
	private Vector<InterruptController> intrs;
	private Vector<DMAController> dmas;
	private Memory mem;
	private ELFFrontPanel fp;
	private boolean running;
	private boolean stopped;
	private Semaphore stopWait;
	private boolean tracing;
	private int traceCycles;
	private int traceLow;
	private int traceHigh;
	private int intRegistry;
	private int intLines;
	private int dmaInLines;
	private int dmaOutLines;
	private int[] efLines;
	private int intMask;
	private Vector<ClockListener> clks;
	private Vector<TimeListener> times;
	private	int cpuSpeed = 2000000;
	private int cpuCycle2ms = 4000;
	private int nanoSecCycle = 500;
	private CDP1802Disassembler disas;
	private ReentrantLock cpuLock;
	private int iodecoder = Interruptor.SIMPLE;

	// Missed 2mS interrupt statistics
	private long backlogNs;

	public ELFFrontPanel getFrontPanel() { return fp; }

	public COSMAC_ELF(Properties props, String lh) {
		String s;
		intMask = 0;
		intRegistry = 0;
		intLines = 0;
		running = false;
		stopped = true;
		stopWait = new Semaphore(0);
		cpuLock = new ReentrantLock();
		devs = new Vector<IODevice>();
		clks = new Vector<ClockListener>();
		times = new Vector<TimeListener>();
		intrs = new Vector<InterruptController>();
		dmas = new Vector<DMAController>();
		efLines = new int[4];

		// Do this early, so we can log messages appropriately.
		s = props.getProperty("log");
		if (s != null) {
			String[] args = s.split("\\s");
			boolean append = false;
			for (int x = 1; x < args.length; ++x) {
				if (args[x].equals("a")) {
					append = true;
				}
			}
			try {
				FileOutputStream fo = new FileOutputStream(args[0], append);
				PrintStream ps = new PrintStream(fo, true);
				System.setErr(ps);
			} catch (Exception ee) {}
		}
		s = props.getProperty("configuration");
		if (s == null) {
			System.err.format("No config file found\n");
		} else {
			System.err.format("Using configuration from %s\n", s);
		}

		s = props.getProperty("iodecoder");
		if (s != null) {
			if (s.equalsIgnoreCase("simple")) {
				iodecoder = Interruptor.SIMPLE;
			} else if (s.equalsIgnoreCase("strict")) {
				iodecoder = Interruptor.STRICT;
			} else if (s.equalsIgnoreCase("extended")) {
				iodecoder = Interruptor.EXTENDED;
				// TODO: add extended decoder hardware
			} else {
				System.err.format("Invalid 'iodecoder' value \"%s\"\n", s);
			}
		}

		cpu = new CDP1802(this);
		mem = new ELFMemory(props);
		fp = new ELFFrontPanel(props, this);

		addDevice(fp);
		dmas.add(fp);

		if (props.getProperty("hexkeypad") != null) {
			addDevice(new HexKeyPad(props, this));
		}
		if (props.getProperty("pixie") != null) {
			PixieCrt crt = new PixieCrt(props, this);
			addDevice(crt);
			dmas.add(crt);
		}

		disas = new CDP1802Disassembler(mem);
	}

	public void reset() {
		boolean wasRunning = running;
		tracing = false;
		traceCycles = 0;
		traceLow = 0;
		traceHigh = 0;
		// TODO: reset other interrupt state? devices should do that...
		intMask = 0;
		clock = 0;
		stop();
		if (false && wasRunning) {
			System.err.format("backlogNs=%d\n", backlogNs);
		}
		backlogNs = 0;
		cpu.reset();
		mem.reset();
		for (int x = 0; x < devs.size(); ++x) {
			devs.get(x).reset();
		}
		if (wasRunning) {
			start();
		}
	}

	public boolean addDevice(IODevice dev) {
		if (dev == null) {
			System.err.format("NULL I/O device\n");
			return false;
		}
		int base = dev.getBaseAddress();
		int num = dev.getMask();
		if (num == 0) {
			//System.err.format("No ports\n");
			//return false;
			return true;
		}
		devs.add(dev);
		return true;
	}

	// These must NOT be called from the thread...
	public void start() {
		stopped = false;
		if (running) {
			return;
		}
		running = true;
		Thread t = new Thread(this);
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();
	}
	public void stop() {
		stopWait.drainPermits();
		if (!running) {
			return;
		}
		running = false;
		// This is safer than spinning, but still stalls the thread...
		try {
			stopWait.acquire();
		} catch (Exception ee) {}
	}

	private void addTicks(int ticks) {
		clock += ticks;
		for (ClockListener lstn : clks) {
			lstn.addTicks(ticks, clock);
		}
		int t = ticks * nanoSecCycle;
		for (TimeListener lstn : times) {
			lstn.addTime(t);
		}
	}

	// I.e. admin commands to virtual H89...
	public ELFCommander getCommander() {
		return (ELFCommander)this;
	}

	// TODO: these may be separate classes...

	/////////////////////////////////////////////
	/// Interruptor interface implementation ///
	public int IODecoder() { return iodecoder; }

	public int registerINT() {
		int val = intRegistry++;
		// TODO: check for overflow (32 bits max?)
		return val;
	}
	public synchronized void raiseINT(int src) {
		intLines |= (1 << src);
		if (intLines != 0) {
			cpu.setINTLine(true);
		}
	}
	public synchronized void lowerINT(int src) {
		intLines &= ~(1 << src);
		if (intLines == 0) {
			cpu.setINTLine(false);
		}
	}
	public synchronized void raiseDMA_IN(int src) {
		dmaInLines |= (1 << src);
		if (dmaInLines != 0) {
			cpu.setDMAin(true);
		}
	}
	public synchronized void lowerDMA_IN(int src) {
		dmaInLines &= ~(1 << src);
		if (dmaInLines == 0) {
			cpu.setDMAin(false);
		}
	}
	public synchronized void raiseDMA_OUT(int src) {
		dmaOutLines |= (1 << src);
		if (dmaOutLines != 0) {
			cpu.setDMAout(true);
		}
	}
	public synchronized void lowerDMA_OUT(int src) {
		dmaOutLines &= ~(1 << src);
		if (dmaOutLines == 0) {
			cpu.setDMAout(false);
		}
	}
	public synchronized void setEF(int src, int ef, boolean on) {
		if (on) {
			efLines[ef] |= (1 << src);
			cpu.setEF(ef, on);
		} else {
			efLines[ef] &= ~(1 << src);
			if (efLines[ef] == 0) {
				cpu.setEF(ef, on);
			}
		}
	}

	public synchronized void setSwitch(int sw, boolean on) {
		if (sw == ELFFrontPanel.RUN) {
			cpu.setCLEAR(!on);
			if (cpu.getState() == CDP1802.State.RESET) {
				reset();
			}
		} else if (sw == ELFFrontPanel.LOAD) {
			cpu.setWAIT(on);
			if (cpu.getState() == CDP1802.State.RESET) {
				reset();
			}
		} else if (sw == ELFFrontPanel.PROM) {
			mem.setROM(on);
		}
	}
	public void blockInts(int msk) {
		intMask |= msk;
		if (false) {
			cpu.setINTLine(false);
		}
	}
	public void unblockInts(int msk) {
		intMask &= ~msk;
		if (false) {
			cpu.setINTLine(true);
		}
	}
	public void addClockListener(ClockListener lstn) {
		clks.add(lstn);
	}
	public void addTimeListener(TimeListener lstn) {
		times.add(lstn);
	}
	public void addIntrController(InterruptController ctrl) {
		// There really should be only zero or one.
		intrs.add(ctrl);
	}
	public void addDMAController(DMAController ctrl) {
		// There really should be only zero or one.
		dmas.add(ctrl);
	}
	public void waitCPU() {
		// Keep issuing clock cycles while stalling execution.
		addTicks(1);
	}
	public boolean isTracing() {
		return tracing;
	}
	public void startTracing(int cy) {
		if (cy > 0) {
			traceCycles = cy;
		} else {
			tracing = true;
		}
	}
	public void stopTracing() {
		traceLow = traceHigh = 0;
		traceCycles = 0;
		tracing = false;
	}

	/////////////////////////////////////////////
	/// ELFCommander interface implementation ///
	public Vector<String> sendCommand(String cmd) {
		// TODO: stop CPU during command? Or only pause it?
		String[] args = cmd.split("\\s");
		Vector<String> ret = new Vector<String>();
		ret.add("ok");
		Vector<String> err = new Vector<String>();
		err.add("error");
		if (args.length < 1) {
			return ret;
		}
		if (args[0].equalsIgnoreCase("quit")) {
			// Release Z80, if held...
			stop();
			System.exit(0);
		}
		if (args[0].equalsIgnoreCase("trace") && args.length > 1) {
			if (!traceCommand(args, err, ret)) {
				return err;
			}
			return ret;
		}
		try {
			cpuLock.lock(); // This might sleep waiting for CPU to finish 2mS
			if (args[0].equalsIgnoreCase("reset")) {
				reset();
				return ret;
			}
			if (args[0].equalsIgnoreCase("dump") && args.length > 1) {
				if (args[1].equalsIgnoreCase("core") && args.length > 2) {
					mem.dumpCore(args[2]);
				}
				if (args[1].equalsIgnoreCase("cpu")) {
					ret.add(cpu.dumpDebug());
					ret.add(disas.disas(cpu.getRegPC()) + "\n");
				}
				if (args[1].equalsIgnoreCase("page") && args.length > 2) {
					String s = dumpPage(args);
					if (s == null) {
						err.add("syntax");
						err.addAll(Arrays.asList(args));
						return err;
					}
					ret.add(s);
				}
				if (args[1].equalsIgnoreCase("disas") && args.length > 3) {
					String s = dumpDisas(args);
					if (s == null) {
						err.add("syntax");
						err.addAll(Arrays.asList(args));
						return err;
					}
					ret.add(s);
				}
				if (args[1].equalsIgnoreCase("mach")) {
					ret.add(dumpDebug());
				}
				if (args[1].equalsIgnoreCase("dev") && args.length > 2) {
					IODevice dev = findDevice(args[2]);
					if (dev == null) {
						err.add("nodevice");
						err.add(args[2]);
						return err;
					}
					ret.add(dev.dumpDebug());
				}
				return ret;
			}
			if (args[0].equalsIgnoreCase("load") && args.length > 2) {
				// assume load core (prog) [adr]
				int adr = 0;
				if (args.length > 3) {
					adr = Integer.valueOf(args[3], 16);
				}
				mem.load(args[2], adr);
				return ret;
			}
			if (args[0].equalsIgnoreCase("copy") && args.length > 1) {
				// for now, assume all are "ROM to RAM"...
				mem.copy();
				ret.add("ROM transferred to RAM");
				return ret;
			}
			if (args[0].equalsIgnoreCase("getdevs")) {
				for (IODevice dev : devs) {
					String nm = dev.getDeviceName();
					if (nm != null) {
						ret.add(nm);
					}
				}
				return ret;
			}
			err.add("badcmd");
			err.add(cmd);
			return err;
		} finally {
			cpuLock.unlock();
		}
	}

	private boolean traceCommand(String[] args, Vector<String> err,
			Vector<String> ret) {
		// TODO: do some level of mutexing?
		if (args[1].equalsIgnoreCase("on")) {
			startTracing(0);
		} else if (args[1].equalsIgnoreCase("off")) {
			stopTracing();
		} else if (args[1].equalsIgnoreCase("cycles") && args.length > 2) {
			try {
				traceCycles = Integer.valueOf(args[2]);
			} catch (Exception ee) {}
		} else if (args[1].equalsIgnoreCase("pc") && args.length > 2) {
			// TODO: this could be a nasty race condition...
			try {
				traceLow = Integer.valueOf(args[2], 16);
			} catch (Exception ee) {}
			if (args.length > 3) {
				try {
					traceHigh = Integer.valueOf(args[3], 16);
				} catch (Exception ee) {}
			} else {
				traceHigh = 0x10000;
			}
			if (traceLow >= traceHigh) {
				traceLow = traceHigh = 0;
			}
		} else {
			err.add("unsupported:");
			err.add(args[1]);
			return false;
		}
		return true;
	}

	private IODevice findDevice(String name) {
		for (IODevice dev : devs) {
			if (name.equals(dev.getDeviceName())) {
				return dev;
			}
		}
		return null;
	}

	public void setSpeed(int spd) {
		// spd is in Hz...
		cpuSpeed = spd;
		// TODO: how does this affect the execute loop?
		// Do we need to adjust something? reset the loop?
		cpuCycle2ms = spd / 500;
		nanoSecCycle = 1000000000 / spd;
	}

	/////////////////////////////////////////
	/// Computer interface implementation ///

	public int peek8(int address) {
		int val = mem.read(address);
		return val;
	}
	public void poke8(int address, int value) {
		// TODO: how much of a cheat is this?
		if (!fp.getMP()) mem.write(address, value);
		if (fp.getLOAD()) {
			fp.setDisplay(mem.read(address));
		}
	}

	public int inPort(int port) {
		int val = 0;
		for (IODevice dev : devs) {
			if ((dev.getDevType() & IODevice.IN) != 0 &&
				(port & dev.getMask()) == dev.getBaseAddress()) {
				// last matching port wins...
				val = dev.in(port);
			}
		}
		return val;
	}
	public void outPort(int port, int value) {
		for (IODevice dev : devs) {
			if ((dev.getDevType() & IODevice.OUT) != 0 &&
				(port & dev.getMask()) == dev.getBaseAddress()) {
				// all matching ports get output
				dev.out(port, value);
			}
		}
	}

	public void setQ(boolean on) {
		if (fp != null) fp.setQLed(on);
	}

	public int dmaIn() {
		for (DMAController ctrl : dmas) {
			if (ctrl.isActive(true)) {
				return ctrl.readDataBus();
			}
		}
		return 0;
	}
	public void dmaOut(int val) {
		for (DMAController ctrl : dmas) {
			if (ctrl.isActive(false)) {
				ctrl.writeDataBus(val);
				return;
			}
		}
	}

	public boolean intEnabled() {
		return cpu.isIE();
	}

	public boolean isRunning() {
		return !stopped;
	}

	//////// Runnable /////////
	public void run() {
		String traceStr = "";
		int clk = 0;
		int limit = 0;
		while (running) {
			cpuLock.lock(); // This might sleep waiting for GUI command...
			limit += cpuCycle2ms;
			long t0 = System.nanoTime();
			int traced = 0; // assuming any tracing cancels 2mS accounting
			while (running && limit > 0) {
				int PC = cpu.getRegPC();
				boolean trace = tracing;
				if (!trace && (traceCycles > 0 ||
						(PC >= traceLow && PC < traceHigh))) {
					trace = true;
				}
				if (trace) {
					++traced;
					int XX = cpu.getRegXX();
					traceStr = String.format("{%05d} %04x: %02x %02x %02x %02x " +
						": %02x %04x %02x %04x <%02x/%02x>%s",
						clock & 0xffff,
						PC, mem.read(PC), mem.read(PC + 1),
						mem.read(PC + 2), mem.read(PC + 3),
						// TODO: which registers...
						cpu.getRegD(),XX,mem.read(XX),
						cpu.getReg(0),
						intLines, intMask,
						cpu.isINTLine() ? " INT" : "");
				}
				clk = cpu.execute();
				if (clk < 0) {
					clk = -clk;
					if (trace) {
						System.err.format("%s {%d} *%s*\n",
							traceStr, clk, cpu.specialCycle());
					}
				} else if (trace) {
					// TODO: collect data after instruction?
					System.err.format("%s {%d} %s\n", traceStr, clk,
						disas.disas(PC));
				}
				limit -= clk;
				if (traceCycles > 0) {
					traceCycles -= clk;
				}
				addTicks(clk);
			}
			cpuLock.unlock();
			if (!running) {
				break;
			}
			long t1 = System.nanoTime();
			if (traced == 0) {
				backlogNs += (2000000 - (t1 - t0));
				t0 = t1;
				if (backlogNs > 10000000) {
					try {
						Thread.sleep(10);
					} catch (Exception ee) {}
					t1 = System.nanoTime();
					backlogNs -= (t1 - t0);
				}
			}
			t0 = t1;
		}
		stopped = true;
		stopWait.release();
	}

	public String dumpPage(String[] args) {
		String str = "";
		int pg = 0;
		int i = 2;
		boolean rom = false;
		if (args[i].equalsIgnoreCase("rom")) {
			rom = true;
			++i;
		}
		if (args.length - i < 1) {
			return null;
		}
		try {
			pg = Integer.valueOf(args[i++], 16);
		} catch (Exception ee) {
			return ee.getMessage();
		}
		int adr = pg << 8;
		int end = adr + 0x0100;
		while (adr < end) {
			str += String.format("%04x:", adr);
			for (int x = 0; x < 16; ++x) {
				int c;
				c = mem.read(adr + x);
				str += String.format(" %02x", c);
			}
			str += "  ";
			for (int x = 0; x < 16; ++x) {
				int c;
				c = mem.read(adr + x);
				if (c < ' ' || c > '~') {
					c = '.';
				}
				str += String.format("%c", (char)c);
			}
			str += '\n';
			adr += 16;
		}
		return str;
	}

	public String dumpDisas(String[] args) {
		int lo = 0;
		int hi = 0;
		String ret = "";
		boolean rom = false;
		int i = 2;
		if (args[i].equalsIgnoreCase("rom")) {
			rom = true;
			++i;
		}
		try {
			lo = Integer.valueOf(args[i++], 16);
			hi = Integer.valueOf(args[i++], 16);
		} catch (Exception ee) {
			return ee.getMessage();
		}
		for (int a = lo; a < hi;) {
			String d;
			ret += String.format("%04X:", a);
			d = disas.disas(a);
			int n = disas.instrLen();
			for (int x = 0; x < n; ++x) {
				int b;
				b = mem.read(a + x);
				ret += String.format(" %02X", b);
			}
			a += n;
			while (n < 4) {
				ret += "   ";
				++n;
			}
			ret += ' ';
			ret += d;
			ret += '\n';
		}
		return ret;
	}

	public String dumpDebug() {
		String ret = "";
		ret += String.format("System Clock %d Hz\n", cpuSpeed);
		ret += String.format("CLK %d", clock);
		if (running) {
			ret += " RUN";
		}
		if (stopped) {
			ret += " STOP";
		}
		if (!running && !stopped) {
			ret += " limbo";
		}
		ret += "\n";
		ret += String.format("2mS Backlog = %d nS\n", backlogNs);
		ret += String.format("INT = { %02x %02x }\n", intLines, intMask);
		return ret;
	}
}
