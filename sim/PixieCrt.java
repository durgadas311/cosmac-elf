// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>
import java.util.Arrays;
import java.util.Properties;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.concurrent.Semaphore;

public class PixieCrt extends JPanel
		implements IODevice, DMAController, ClockListener {
	static final int _sw = 320;	// screen width
	static final int _sh = 240;	// screen height
	private boolean disable;	// allow CDP1861 disable via OUT
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
	private int time = 0;
	private enum State { BLANKING, EFX1, INT, DMA, GAP, EFX2 };
	private State state = State.BLANKING;
	private int ioa = 0b001;
	private int iom = 0b001;
	private int efn = 0;

	public PixieCrt(Properties props, Interruptor intr) {
		super();
		this.intr = intr;
		src = intr.registerINT();
		intr.addClockListener(this);
		String s = props.getProperty("pixie_port");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 7) {
				System.err.format("Invalid pixie_port: %d\n", n);
			} else {
				ioa = n;
			}
		}
		if (intr.IODecoder() == Interruptor.SIMPLE) {
			// assumes 'ioa' has only 1 bit set
			iom = ioa; // not always simple... e.g. ioa=7
		} else {
			iom = 0b111; // require exact match
		}
		s = props.getProperty("pixie_ef");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 4) {
				System.err.format("Invalid pixie_ef: %d\n", n);
			} else {
				efn = n - 1;
			}
		}

		phosphor = new Color(0, 255, 0);
		s = props.getProperty("pixie_color");
		if (s != null) {
			phosphor = new Color(Integer.valueOf(s, 16));
		}
		// Allow CDP1861 disable via OUT?
		disable = (props.getProperty("pixie_disable") != null);
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
		frame.setLocationByPlatform(true);
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
		System.err.format("PixieCrt at port %d mask %d EF%d%s\n",
			ioa, iom, efn + 1, test ? " (test)" : "");
	}

	public void reset() {
		if (test) return;
		intr.lowerDMA_OUT(src);
		intr.lowerINT(src);
		intr.setEF(src, efn, false);
		enabled = false;
		time = 0;
		state = State.BLANKING;
		dma = false;
		efx = false;
		intn = false;
		bc = 0;
		Arrays.fill(crt, (byte)0);
		repaint();
	}

	public int getBaseAddress() { return ioa; }
	public int getMask() { return iom; }
	public int getDevType() { return IODevice.IN_OUT; }	// only INP used?

	public int in(int port) {
		enabled = true;
		state = State.BLANKING;
		time = 64;	// a little delay
		return 0;
	}

	public void out(int port, int value) {
		// data ignored
		if (disable) {
			enabled = false;
			state = State.BLANKING;
			time = 64;	// a little delay
			repaint();
		} else {
			enabled = true;
			state = State.BLANKING;
			time = 64;	// a little delay
		}
	}

	public String getDeviceName() { return "PIXIE"; }

	public String dumpDebug() {
		String str = "ELF Pixie Graphics\n";
		str += String.format("enabled=%s test=%s\n", enabled, test);
		str += String.format("DMAO=%s @ %d EFX=%s INT=%s\n", dma, bc, efx, intn);
		//str += String.format("last frame=%d bytes=%d\n", last, lastn);
		str += String.format("state=%s time=%d\n", state.name(), time);
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
	public void addTicks(int ticks, long clk) {
		if (!enabled) return;
		if (time <= 0) return;
		time -= ticks;
		if (time > 0) return;
		// update state...
		switch(state) {
		case BLANKING:
			time = 232;
			state = State.EFX1;
			efx = true;
			intr.setEF(src, efn, true);
			break;
		case EFX1:
			time = 232;
			state = State.INT;
			intn = true;
			intr.raiseINT(src);
			break;
		case INT:
			time = 64;	// need something... 8 bytes
			state = State.DMA;
			intr.lowerINT(src);
			intr.setEF(src, efn, false);
			efx = false;
			intn = false;
			synchronized(this) {
				bc = 0;
				dma = true;
				intr.raiseDMA_OUT(src);
			}
			break;
		case DMA:
			synchronized(this) {
			if (!dma) {
				if (bc >= 1024) {
					state = State.BLANKING;
					time = 13224;
					intr.setEF(src, efn, false);
					efx = false;
					repaint();
				} else {
					state = State.GAP;
					time = 48;
				}
			}
			}
			break;
		case GAP:
			synchronized(this) {
			if (bc == 992) { // last 4 rows...
				efx = true;
				intr.setEF(src, efn, true);
			}
			}
			time = 64;	// need something...
			state = State.DMA;
			synchronized(this) {
				dma = true;
				intr.raiseDMA_OUT(src);
			}
			break;
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
}
