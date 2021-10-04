// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>
import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.concurrent.Semaphore;

public class PixieCrt extends JPanel
		implements IODevice, DMAController, ClockListener, Runnable {
	static final int _sw = 320;	// screen width
	static final int _sh = 240;	// screen height
	private boolean enabled;
	private byte[] crt = new byte[1024];	// 8x128 bytes = 64x128 pixels
	private int bd_width;
	private Color phosphor;
	private Color bg;
	private JFrame frame;
	private boolean dma = false;
	private boolean efx = false;
	private boolean intn = false;
	private long last;
	private int lastn;
	private Interruptor intr;
	private int src;
	private int bc;
	private int _pw;	// pixel width
	private int _ph;	// pixel height
	private int _h1;	// first horiz line
	private int _p1;	// horiz offset
	private boolean test;
	private Semaphore sem;
	private int time = 0;

	public PixieCrt(Properties props, Interruptor intr) {
		super();
		this.intr = intr;
		src = intr.registerINT();
		intr.addClockListener(this);
		sem = new Semaphore(0);
		phosphor = new Color(0, 255, 0);
		String s = props.getProperty("pixie_color");
		if (s != null) {
			phosphor = new Color(Integer.valueOf(s, 16));
		}
		bd_width = 3;
		bg = new Color(50,50,50, 255);
		frame = new JFrame("PIXIE Graphics Display");
		frame.setBackground(bg);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		// based on 320x240 screen, 64x128 cells.
		// TODO: These need to evenly divide...
		_pw = 4;
		_ph = 1;
		_h1 = 60;	// in raw CRT units, not pixels
		_p1 = 32;	// in raw CRT units, not pixels
		setPreferredSize(new Dimension(_sw + 2 * bd_width, _sh + 2 * bd_width));
		setBackground(bg);
		setForeground(phosphor);
		setOpaque(true);
		frame.add(this);
		reset();
		s = props.getProperty("pixie_test");
		test = (s != null);
		// Go live...
		frame.pack();
		frame.setVisible(true);
		if (test) {
			for (int x = 0; x < crt.length; ++x) {
				if ((x & ~7) == 0) crt[x] = (byte)0xff;
				else if ((x & ~7) == 0x3f8) crt[x] = (byte)0xff;
				else if ((x & 7) == 0) crt[x] = (byte)0x80;
				else if ((x & 7) == 7) crt[x] = (byte)0x01;
				else crt[x] = (byte)0xaa;
			}
			enabled = true;
			repaint();
		}
		Thread t = new Thread(this);
		t.start();
	}

	public void reset() {
		if (test) return;
		intr.lowerDMA_OUT(src);
		intr.lowerINT(src);
		intr.setEF(src, 0, false);
		enabled = false;
		dma = false;
		efx = false;
		intn = false;
		bc = 0;
		Arrays.fill(crt, (byte)0);
		repaint();
	}

	public int getBaseAddress() { return 0b001; }
	public int getMask() { return 0b001; }

	public int in(int port) {
		enabled = true;
		return 0;
	}

	public void out(int port, int value) {
		// data ignored
		enabled = true;
		repaint();
	}

	public String getDeviceName() {
		return "CDP1861";
	}

	public String dumpDebug() {
		String str = "ELF Pixie Graphics\n";
		str += String.format("enabled=%s test=%s\n", enabled, test);
		str += String.format("DMAO=%s @ %d EFX=%s INT=%s\n", dma, bc, efx, intn);
		str += String.format("last frame=%d bytes=%d\n", last, lastn);
		return str;
	}

	// DMAController
	public synchronized boolean isActive(boolean in) { return !in && dma; }
	public int readDataBus() { return 0; }
	public void writeDataBus(int val) {
		synchronized(this) {
			crt[bc++] = (byte)val;
			if ((bc & 7) == 0) {
				dma = false;
				intr.lowerDMA_OUT(src);
			}
		}
	}

	// ClockListener
	public synchronized void addTicks(int ticks, long clk) {
		if (time <= 0) return;
		time -= ticks;
		if (time <= 0) {
			sem.release();
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
		if (!enabled) {
			return;
		}
		Graphics2D g2d = (Graphics2D)g;
		g2d.setColor(getForeground());
		for (int i = 0; i < crt.length; ++i) {
			int y = (i >> 3);
			int x = (i & 7) << 3;
			byte b = crt[i];
			while (b != 0) {
				if ((b & 0x80) != 0) {
					g2d.fillRect(x * _pw + bd_width + _p1,
						y * _ph + bd_width + _h1,
						_pw, _ph);
				}
				b <<= 1;
				++x;
			}
		}
	}

	private void sleep(int delay) {
		synchronized(this) {
			time = delay;
			sem.drainPermits();
		}
		try {
			sem.acquire();
		} catch (Exception ee) {}
	}

	// Perform CRT scan timing
	public void run() {
		while (true) {
			long t0 = System.nanoTime();
			if (!enabled || test) {
				sleep(33333);	// 1/60 second (1 field)
				continue;
			}
			efx = true;
			intr.setEF(src, 0, true);
			sleep(256);	// 2 H-lines
			intn = true;
			intr.raiseINT(src);
			sleep(256);	// 2 H-lines
			intr.lowerINT(src);
			intr.setEF(src, 0, false);
			efx = false;
			intn = false;
			sleep(256);	// ???
			synchronized(this) {
				bc = 0;
			}
			for (int x = 0; x < 128; ++x) {
				synchronized(this) {
					dma = true;
					intr.raiseDMA_OUT(src);
				}
				while (dma) {	// TODO: need timeout
					synchronized(this) {}
				}
				sleep(64);	// 1/2 H-lines
			}
			lastn = bc;
			repaint();
			sleep(14503);	// video blanking... 60+54 H-lines.
			try { Thread.sleep(4,317464); } catch (Exception ee) {}
			last = System.nanoTime() - t0;
		}
	}
}
