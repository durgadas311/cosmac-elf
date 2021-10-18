// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.io.*;
import java.lang.reflect.Constructor;

public class CDP1854 implements IODevice, VirtualUART {
	static final int fifoLimit = 10; // should never even exceed 2
	private Interruptor intr;
	private int src;
	private int ioa = 0b000;
	private int iom = 0b000;
	private String name = null;
	private String prefix = null;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifo;
	private java.util.concurrent.LinkedBlockingDeque<Integer> fifi;
	private int MCR;
	private int MSR;
	private int modem;

	private static final int MSR_DA = 0x01;
	private static final int MSR_OE = 0x02;
	private static final int MSR_PE = 0x04;
	private static final int MSR_FE  = 0x08;
	private static final int MSR_ES = 0x10;
	private static final int MSR_PSI  = 0x20;
	private static final int MSR_TSRE = 0x40;
	private static final int MSR_THRE = 0x80;
	private static final int MSR_RXERR = (MSR_FE|MSR_PE|MSR_OE);
	private static final int MSR_STATIC = (MSR_ES|MSR_PSI);
	private static final int MSR_CTS = 0x100;	// not SW accessible...

	private static final int MCR_PI = 0x01;
	private static final int MCR_EPE = 0x02;
	private static final int MCR_WLSB = 0x1c;	// word len, stop bit
	private static final int MCR_IE = 0x20;
	private static final int MCR_BRK = 0x40;
	private static final int MCR_TR = 0x80;		// set RTS, enable THRE intr
							// (if MCR_IE is set)

	private Object attObj;
	private SerialDevice io;
	private boolean io_in = false;
	private boolean io_out = false;
	private boolean excl = true;
	private long lastTx = 0;
	private long lastRx = 0;
	private int clock = 153600;	// Hz, both TxC and RxC (9600)
	private long nanoBaud = 0; // length of char in nanoseconds
	private int bits = 8; // bits per character

	public CDP1854(Properties props, String pfx,
			Interruptor intr) {
		prefix = pfx;
		name = pfx.toUpperCase() + "_CDP1854";
		attObj = null;
		this.intr = intr;
		src = intr.registerINT();
		String s;
		s = props.getProperty(pfx + "_port");
		if (s != null) {
			int i = Integer.decode(s);
			if (i >= 1 && i <= 7) {
				ioa = i;
			}
		}
		if (ioa != 0) {
			if (intr.IODecoder() == Interruptor.SIMPLE) {
				// TODO: incompatible?
				iom = ioa | 1; // not always simple... e.g. ioa=7
			} else {
				iom = 0b110;	// two ports...
			}
		}
		// TODO: allow separate Rx and Tx clocks?
		// TODO: select popular default...
		// TODO: external programmable clock source
		s = props.getProperty(pfx + "_clock");
		if (s != null) {
			int i = Integer.valueOf(s);
			if (i > 0 && i <= 400000) {
				clock = i;
			}
		}
		int baud = clock / 16;
		int cps = baud / bits;
		nanoBaud = (1000000000 / cps);
		fifo = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		fifi = new java.util.concurrent.LinkedBlockingDeque<Integer>();
		reset();
		s = props.getProperty(pfx + "_att");
		if (s != null && s.length() > 1) {
			attachClass(props, s);
		}
		System.err.format("CDP1854 at port %d mask %d\n", ioa, iom);
	}

	private void attachClass(Properties props, String s) {
		String[] args = s.split("\\s");
		Vector<String> argv = new Vector<String>(Arrays.asList(args));
		// try to construct from class...
		try {
			Class<?> clazz = Class.forName(args[0]);
			Constructor<?> ctor = clazz.getConstructor(
					Properties.class,
					argv.getClass(),
					VirtualUART.class);
			// funky "new" avoids "argument type mismatch"...
			attObj = ctor.newInstance(
					props,
					argv,
					(VirtualUART)this);
			System.err.format("CDP1854 attached %s\n", s);
		} catch (Exception ee) {
			System.err.format("Invalid class in attachment: %s\n", s);
			return;
		}
	}

	public void attachDevice(SerialDevice io) {
		this.io = io;
		io_in = (io != null && (io.dir() & SerialDevice.DIR_IN) != 0);
		io_out = (io != null && (io.dir() & SerialDevice.DIR_OUT) != 0);
	}

	// Conditions affecting interrupts have changed, ensure proper signal.
	private void chkIntr() {
		boolean ion = ((MCR & MCR_IE) != 0);
		if (ion) {
			intr.raiseINT(src);
		} else {
			intr.lowerINT(src);
		}
	}

	private void updateStatus() {
		long t = System.nanoTime();
		// TODO: factor in RxE/TxE/CTS?
		MSR &= ~MSR_DA;
		if (io_in) {
			if (io.available() > 0) {
				MSR |= MSR_DA;
			}
		} else {
			// simulate Rx overrun from neglect...
			while (t - lastRx > 30000000 && fifi.size() > 1) {
				try {
					fifi.take();
				} catch (Exception ee) {}
				MSR |= MSR_OE;
			}
			if (fifi.size() > 0) {
				MSR |= MSR_DA;
			}
			lastRx = t;
		}
		if (t - lastTx > nanoBaud) {
			if (io_out) {
				MSR |= MSR_THRE;
				MSR |= MSR_TSRE;
				lastTx = t;
			} else if (fifo.size() < 2) {
				MSR |= MSR_THRE;
				if (fifo.size() < 1) {
					MSR |= MSR_TSRE;
				} else {
					lastTx = t;
				}
			}
		}
	}

	///////////////////////////////
	/// Interfaces for IODevice ///
	public int in(int port) {
		int off = port & 1;
		int val = 0;
		switch(off) {
		case 0: // Rx Data
			if (io_in) {
				val = io.read();
				if (val >= 0) {
					break;
				}
				MSR &= ~MSR_DA;
				chkIntr();
				break;
			}
			synchronized(this) {
				if (fifi.size() > 0) {
					try {
						val = fifi.take();
					} catch (Exception ee) {}
					if (fifi.size() == 0) {
						MSR &= ~MSR_DA;
						chkIntr();
					}
				}
			}
			break;
		case 1:
			updateStatus();
			val = MSR & 0xff;
			// TODO: nothing special here for TxEnable?
			break;
		}
		return val;
	}

	public void out(int port, int val) {
		int off = port & 1;
		val &= 0xff; // necessary?
		switch(off) {
		case 0: // Tx Data
			if (!canTx()) {
				break;
			}
			if (io_out) {
				io.write(val);
			}
			if (!io_out || !excl) {
				fifo.add(val);
				lastTx = System.nanoTime();
				MSR &= ~MSR_THRE;
				if (fifo.size() > 1) {
					MSR &= ~MSR_TSRE;
					try {
						while (fifo.size() > fifoLimit) {
							fifo.removeFirst();
						}
					} catch (Exception ee) {}
				} else {
					// probably already set
					MSR |= MSR_TSRE;
				}
				chkIntr();
			}
			break;
		case 1:
			if ((val & MCR_TR) != 0) {
				MCR |= MCR_TR;
			} else {
				MCR = val;
				setMode();
			}
			changeModem();
			break;
		}
	}

	private void setMode() {
		int val = MCR & 0xff;
		bits = ((val >> 3) & 0x03) + 5 + 1; // incl START
		if ((val & 0x04) != 0) {
			++bits;	// 1.5 or 2 stop bits...
		}
		if ((val & 0x01) != 0) {
			++bits;	// parity bit
		}
	}

	public void reset() {
		MSR &= ~MSR_STATIC;
		MSR |= (MSR_THRE | MSR_TSRE);
		MCR = 0;
		changeModem();
		chkIntr();
		fifo.clear();
		fifi.clear();
	}
	public int getBaseAddress() { return ioa; }
	public int getMask() { return iom; }
	public int getDevType() { return IODevice.IN_OUT; }

	private void changeModem() {
		if (io == null) {
			return;
		}
		int m = getModem();
		int diff = modem ^ m;
		if (diff == 0) {
			return;
		}
		modem = m;
		io.modemChange(this, modem);
	}

	private boolean canTx() {
		// NOTE: !MCR_TR does not inhibit Tx...
		return ((MSR & MSR_CTS) != 0);
	}

	////////////////////////////////////////////////////
	/// Interfaces for the virtual peripheral device ///
	public boolean attach(Object periph) { return false; }
	public void detach() {
		if (attObj == null) {
			return;
		}
		attObj = null;
		excl = true;
		try {
			fifo.addFirst(-1);
		} catch (Exception ee) {
			fifo.add(-1);
		}
	}
	public int available() {
		return fifo.size();
	}

	// Must sleep if nothing available...
	public int take() {
		try {
			int c = fifo.take();
			// Tx always appears empty...
			// But might need to simulate intr.
			// This is separate thread from CPU so must be careful...
			// TBD: MSR |= MSR_TXR; chkIntr();
			return c;
		} catch(Exception ee) {
			return -1;
		}
	}

	public boolean ready() {
		return (MSR & MSR_DA) == 0;
	}
	// Must NOT sleep
	public synchronized void put(int ch, boolean sleep) {
		// TODO: prevent infinite growth?
		fifi.add(ch & 0xff);
		lastRx = System.nanoTime();
		MSR |= MSR_DA;
		chkIntr();
	}

	public void setModem(int mdm) {
		int nuw = MSR;
		if ((mdm & VirtualUART.SET_DSR) != 0) {
			nuw |= MSR_ES;
		}
		if ((mdm & VirtualUART.SET_CTS) != 0) {
			nuw |= MSR_CTS;
		}
		MSR = nuw;
		// TODO: must make this thread-safe...
		chkIntr();
		changeModem();
	}
	public int getModem() {
		int mdm = 0;
		if ((MCR & MCR_TR) != 0) {
			mdm |= VirtualUART.GET_RTS;
		}
		if ((MSR & MSR_THRE) != 0) {
			mdm |= VirtualUART.GET_TXE;
		}
		if ((MSR & MSR_ES) != 0) {
			mdm |= VirtualUART.SET_DSR;
		}
		if ((MSR & MSR_CTS) != 0) {
			mdm |= VirtualUART.SET_CTS;
		}
		return mdm;
	}
	public String getPortId() { return prefix; }

	public String getDeviceName() { return name; }

	public String dumpDebug() {
		String ret = new String();
		ret += String.format("port %d mask %d, #fifo = %d, #fifi = %d\n",
			ioa, iom, fifo.size(), fifi.size());
		ret += String.format("clock = %d nanoBaud = %d\n", clock, nanoBaud);
		ret += String.format("MCR = %02x, MSR = %02x\n", MCR, MSR);
		if (io != null) {
			ret += io.dumpDebug();
		}
		return ret;
	}
}
