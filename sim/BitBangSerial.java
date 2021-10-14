// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.util.Vector;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;
import javax.sound.sampled.*;
import java.lang.reflect.Constructor;

// Serial format (t+ ->)
//
// MARK-----+   +---+---+---+---+---+---+---+---+---+---+-----------
//          | S | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | P   P
// SPACE    +---+---+---+---+---+---+---+---+---+
//
// S = start bit
// P = stop bit(s)
// "echo" on the ELF is a literal reflection of RxD (EFn) back on TxD (Q)
// (in software, so slightly delayed). This module must run full-duplex.

public class BitBangSerial
		implements IODevice, ClockListener, QListener, VirtualUART {

private long clock;
private long lastclk = 0;
	private Interruptor intr;
	private int src;
	private int efn = 2;	// EF3 = RxD
	private long ticks = 0;
	private int baud = 1200;	// 1666 cycles/bit, ~104 instructions
	private int nbit = 8;
	private int nstp = 3;	// need extra for paper tape reader overrun
	private int bclk;
	private int mstp;
	private int mbit;
	private boolean lastQ = false;	// true = MARK
	private int tclk = 0;
	private int tbits = 0;
	private int tbitc = 0;
	private int rclk = 0;
	private int rbits = 0;
	private int rbitc = 0;
	private boolean qmark_1 = true;		// Q=1 is MARK?
	private boolean efmark_1 = false;	// EFn=1 (/EFn=0) is MARK?
	private Object attObj = null;
	private SerialDevice io = null;
	private boolean io_in = false;
	private boolean io_out = false;

	public BitBangSerial(Properties props, Interruptor intr) {
		this.intr = intr;
		src = intr.registerINT();
		intr.addClockListener(this);
		intr.addQListener(this);
		String s = props.getProperty("quart_ef");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 4) {
				System.err.format("Invalid hexkeypad_ef: %d\n", n);
			} else {
				efn = n - 1;
			}
		}
		s = props.getProperty("quart_baud");
		if (s != null) {
			baud = Integer.valueOf(s);
			// TODO: normalize/validate BAUD?
		}
		s = props.getProperty("quart_qmark");
		if (s != null) {
			int n = Integer.valueOf(s);
			qmark_1 = (n != 0);
		}
		s = props.getProperty("quart_efmark");
		if (s != null) {
			int n = Integer.valueOf(s);
			efmark_1 = (n != 0);
		}
		s = props.getProperty("quart_nbit");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n >= 5 && n <= 8) {
				nbit = n;
			}
		}
		bclk = intr.getSpeed() / baud;
		mstp = (0b1111 << (nbit + 1));	// max 2 STOP bits
		mbit = (1 << (nbit - 1));
		s = props.getProperty("quart_att");
		if (s != null && s.length() > 1) {
			attachClass(props, s);
		}

		intr.setEF(src, efn, efmark_1);	// set MARKing line
		System.err.format("BitBangSerial at Q%c, EF%d%c, %d baud, %dN1\n",
			qmark_1 ? '+' : '-',
			efn + 1, efmark_1 ? '+' : '-',
			baud, nbit);
	}

	private void attachClass(Properties props, String s) {
		String[] args = s.split("\\s");
//		for (int x = 1; x < args.length; ++x) {
//			if (args[x].startsWith(">")) {
//				excl = false;
//				args[x] = args[x].substring(1);
//				setupFile(args, x);
//			}
//		}
		Vector<String> argv = new Vector<String>(Arrays.asList(args));
		try{
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
			System.err.format("Q-UART attached %s\n", s);
		} catch (Exception ee) {
			System.err.format("Invalid class in attachment: %s\n", s);
			return;
		}
	}

	public synchronized void addTicks(int tik, long clk) {
		if (tclk > 0) {
			tclk -= tik;
			if (tclk <= 0 && tbitc > 0) {
				// always ends with stop bit(s) = MARK
				boolean bit = ((tbits & 1) != 0);
				intr.setEF(src, efn, efmark_1 ? bit : !bit);
				tbits >>= 1;
				--tbitc;
				if (tbitc > 0) tclk += bclk;
			}
		}
		// Must be last thing...
		if (rclk > 0) {
			rclk -= tik;
			if (rclk <= 0 && rbitc > 0) {
				// TODO: validate and discard START bit
				if (rbitc > nbit + 1 && lastQ) return; // lost START bit
				if (rbitc == 1) {	// STOP bit?
					if (!lastQ) {	// BREAK, not NUL char
						//rclk += bclk;
						rclk += bclk;
						rbits = 0;
						// keep waiting for STOP...
						return;
					}
					rbitc = 0;
					// TODO: io.write(rbits);
					// TODO: spawn off to thread?
					if (io_out) {
						io.write(rbits & 0x7f);
					}
					//System.err.format("> %02x\n", rbits);
					return;
				}
				--rbitc;
				rbits >>= 1;
				if (lastQ) rbits |= mbit;
				//rclk += bclk;
				rclk += bclk;
			}
		}
		// Nothing can follow here...
	}

	private synchronized void xmit(int ch) {
		tbits = (ch << 1) | mstp;
		tbitc = nbit + nstp + 1;
		tclk = 1;	// start immediately
		//System.err.format("< %03x\n", tbits);
	}

	// QListener
	public void setQ(boolean on) {
		lastQ = (qmark_1 ? on : !on);
		if (rbitc == 0 && !lastQ) synchronized(this) {
			// start receive
			// check state after 1/2 bit rate...
			//rclk = bclk / 2;
			rclk = bclk / 2;
			rbits = 0;
			rbitc = nbit + 2;	// at least 1 STOP bit
		}
	}

	// VirtualUART
	public int available() { return 0; }
	public int take() { return 0; }
	public boolean ready() { return tbitc <= 0; }
	public void put(int ch, boolean sleep) {
		// TODO: what if Tx busy?
		// TODO: spawn off to thread?
		if (ready()) {
			xmit(ch);
		}
	}
	public void setModem(int mdm) {}
	public int getModem() { return 0; }
	public boolean attach(Object periph) { return false; }
	public void detach() {
		if (attObj == null) return;
		attObj = null;
		// excl = true;
		// more? notify thread?
		// io.write(-1) ??
	}
	public String getPortId() { return ""; }
	public void attachDevice(SerialDevice io) {
		this.io = io;
		io_in = (io != null && (io.dir() & SerialDevice.DIR_IN) != 0);
		io_out = (io != null && (io.dir() & SerialDevice.DIR_OUT) != 0);
	}

	// IODevice - not a real I/O device...
	public void reset() {}	// TODO: anything?
	public int getBaseAddress() { return 0; }
	public int getMask() { return 0; }	// in()/out() never called
	public int getDevType() { return 0; }	// in()/out() never called
	public int in(int port) { return 0; }
	public void out(int port, int value) {}
	public String getDeviceName() { return "Q-UART"; }
	public String dumpDebug() {
		String ret = String.format("Q-UART Q%c EF%d%c baud=%d %dN1\n",
			qmark_1 ? '+' : '-',
			efn + 1, efmark_1 ? '+' : '-',
			baud, nbit);
		ret += String.format("Tx bitc=%d bits=%03x clk=%d\n", tbitc, tbits, tclk);
		ret += String.format("Rx bitc=%d bits=%03x clk=%d\n", rbitc, rbits, rclk);
		return ret;
	}
}
