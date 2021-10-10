// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;
import javax.sound.sampled.*;

// Serial format (t+ ->)
//
// ---------+   +---+---+---+---+---+---+---+---+---+---+-----------
//          | S | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | P   P
//          +---+---+---+---+---+---+---+---+---+
// S = start bit
// P = stop bit(s)
// "echo" on the ELF is a literal reflection of RxD (EFn) back on TxD (Q)
// (in software, so slightly delayed). This module must run full-duplex.

public class BitBangSerial
		implements IODevice, QListener, VirtualUART {

	private Interruptor intr;
	private int src;
	private int efn = 3;
	private long ticks = 0;
	private int baud = 9600;	// 208 cycles/bit, ~13 instructions
	private int nbit = 8;
	private int nstp = 1;
	private int bclk;
	private int mstp;
	private boolean lastQ = false;
	private int tclk = 0;
	private int tbits = 0;
	private int tbitc = 0;
	private int rclk = 0;
	private int rbits = 0;
	private int rbitc = 0;

	public BitBangSerial(Properties props, Interruptor intr) {
		this.intr = intr;
		src = intr.registerINT();
		intr.addQListener(this);
		String s = props.getProperty("bitbang_ef");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 4) {
				System.err.format("Invalid hexkeypad_ef: %d\n", n);
			} else {
				efn = n - 1;
			}
		}
		s = props.getProperty("bitbang_baud");
		if (s != null) {
			baud = Integer.valueOf(s);
			// TODO: normalize/validate BAUD?
		}
		bclk = intr.getSpeed() / baud;
		mstp = (0b11 << (nbit + 1));	// max 2 STOP bits

		System.err.format("BitBangSerial at Q, EF%d, %d baud\n", efn + 1, baud);
	}

	public synchronized void addTicks(int tik, long clk) {
		if (tclk > 0) {
			tclk -= tik;
			if (tclk <= 0 && tbitc > 0) {
				// always ends with stop bit(s) = MARK
				intr.setEF(src, efn, ((tbits & 1) != 0));
				tbits >>= 1;
				--tbitc;
				tclk = bclk;
			}
		}
		if (rclk > 0) {
			rclk -= tik;
			if (rclk <= 0 && rbitc > 0) {
				// TODO: validate and discard START bit
				--rbitc;
				rbits >>= 1;
				if (lastQ) rbits |= 0x80;
				if (rbitc > 0) rclk = bclk;
				else ; // TODO: io.write(rbits);
			}
		}
	}

	private synchronized void xmit(int ch) {
		tbits = (ch << 1) | mstp;
		tbitc = nbit + nstp + 1;
		tclk = 1;	// start immediately
	}

	// QListener
	public void setQ(boolean on) {
		lastQ = !on;	// Q=1 is SPACE, 0 is MARK
		if (rbitc == 0 && on) synchronized(this) {
			// check state after 1/2 bit rate...
			rclk = bclk / 2;
			rbits = 0;
			rbitc = nbit + 1;
		}
	}

	// VirtualUART
	public int available() { return 0; }
	public int take() { return 0; }
	public boolean ready() { return false; }
	public void put(int ch, boolean sleep) {
		// TODO: what if Tx busy?
		xmit(ch);
	}
	public void setModem(int mdm) {}
	public int getModem() { return 0; }
	public boolean attach(Object periph) { return false; }
	public void detach() {}
	public String getPortId() { return ""; }
	public void attachDevice(SerialDevice io) {}

	// IODevice - not a real I/O device...
	public void reset() {}	// TODO: anything?
	public int getBaseAddress() { return 0; }
	public int getMask() { return 0; }	// in()/out() never called
	public int getDevType() { return 0; }	// in()/out() never called
	public int in(int port) { return 0; }
	public void out(int port, int value) {}
	public String getDeviceName() { return "Q-SERIAL"; }
	public String dumpDebug() {
		// TODO:
		return "";
	}
}
