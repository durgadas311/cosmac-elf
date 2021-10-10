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

public class BitBangSerial
		implements QListener, VirtualUART {

	private Interruptor intr;
	private int src;
	private int efn = 3;

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

		System.err.format("BitBangSerial at Q, EF%d\n", efn + 1);
	}

	// QListener
	public void setQ(boolean on) {
	}

	// VirtualUART
	public int available() { return 0; }
	public int take() { return 0; }
	public boolean ready() { return false; }
	public void put(int ch, boolean sleep) {}
	public void setModem(int mdm) {}
	public int getModem() { return 0; }
	public boolean attach(Object periph) { return false; }
	public void detach() {}
	public String getPortId() { return ""; }
	public void attachDevice(SerialDevice io) {}
}
