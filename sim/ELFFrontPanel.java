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

// Default:
//    0   1    2    3     4     5     6      7    8     9
// 0: +-------------------------------------------------+
// 1: |                     LED                         |
// 2: |                 DISP   DISP                     |
// 3: |                    {ROM}                        |
// 4: |                                                 |
// 5: | <IN> [LOAD]                       [MP]   [RUN]  |
// 6: |                                                 |
// 7: | [7]  [6]   [5]   [4]   [3]   [2]   [1]   [0]    |
// 8: |                                                 |
// 9: +-------------------------------------------------+
//
// Elf-II:
//    0   1    2    3     4     5     6      7    8     9
// 0: +-------------------------------------------------+
// 1: |                                                 |
// 2: |                 DISP   DISP   LED               |
// 3: |                    {ROM}                        |
// 4: |                                                 |
// 5: |      |^^^^^^^^^^^^^^^^^^^|   [RUN]              |
// 6: |      |      KEYPAD       |   [LOAD]             |
// 7: |      |                   |   [MP]               |
// 8: |      |___________________|   <IN>               |
// 9: +-------------------------------------------------+
//
// [..] = toggle switch
// <..> = momentary switch
// {..} = jumper

public class ELFFrontPanel extends JPanel
		implements IODevice, QListener, DMAController,
			MouseListener, ActionListener {
	static final int LOAD = 8;	// index/id of LOAD switch
	static final int MP = 9;	// index/id of MP switch
	static final int RUN = 10;	// index/id of RUN switch
	static final int PROM = 11;	// index/id of PROM jumper
	static final int IN = 12;	// index/id of IN switch
	private Font dspFont;	// or whatever display is configured
	private int fw;
	private Color wdw = new Color(70, 0, 0);
	private Color phenolic = new Color(214, 176, 132);
	private Color soldermask = new Color(175, 191, 160);
	private Color cased = new Color(100, 155, 224);
	private Color pcb;
	private boolean input = false;
	private Interruptor intr;
	private int src;
	private int ioa = 0b100;	// N2 only
	private int iom = 0b100;	// SIMPLE
	private int efn = 3;

	JCheckBox[] btns;
	HexKeyPad kpd;
	JButton in;
	JLabel[] disp;
	GridBagLayout gb;
	GridBagConstraints gc;
	LED qLed;
	boolean aux_disp;
	boolean elf2;
	int width = 400;

	public KeyListener keyListener() { return kpd; }

	public ELFFrontPanel(Properties props, Interruptor intr) {
		super();
		this.intr = intr;
		src = intr.registerINT();
		intr.addDMAController(this);
		intr.addQListener(this);
		elf2 = (intr.getModel() == Interruptor.ELF2);
		String s = props.getProperty("elffrontpanel_port");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 7) {
				System.err.format("Invalid elffrontpanel_port: %d\n", n);
			} else {
				ioa = n;
			}
		}
		if (intr.IODecoder() == Interruptor.SIMPLE) {
			iom = ioa;	// not always simple... e.g. ioa=7
		} else {
			iom = 0b111;	// require exact match
		}
		s = props.getProperty("elffrontpanel_ef");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 4) {
				System.err.format("Invalid elffrontpanel_ef: %d\n", n);
			} else {
				efn = n - 1;
			}
		}
		aux_disp = (props.getProperty("elffrontpanel_aux") != null);
		if (aux_disp) {
			// TODO: allow configuration?
			ioa = 4;
			iom = 4;
		}
		getDispFont(props);

		if (elf2) {
			configElf2(props);
		} else {
			configElf(props);
		}
		// Now safe to do this?
		intr.setSwitch(LOAD, btns[LOAD].isSelected());
		intr.setSwitch(RUN, btns[RUN].isSelected());
		System.err.format("ELFFrontPanel at port %d mask %d EF%d\n",
			ioa, iom, efn + 1);
	}

	private void getDispFont(Properties props) {
		String f = props.getProperty("elffrontpanel_disp");
		if (f == null) {
			f = elf2 ? "FND500" : "TIL311";
		}
		float fz = 35f;	// values for TIL311
		fw = 25;	//
		if (f.equalsIgnoreCase("TIL311")) {
			f = "TIL311.ttf";
		} else if (f.equalsIgnoreCase("FND500")) {
			f = "FND500x.ttf";
			fz = 30f;
			fw = 30;
		} else {
			System.err.format("Unrecognized display type %s\n", f);
			f = "TIL311.ttf";
		}
		try {
			// TODO: search local dir first?
			java.io.InputStream ttf;
			ttf = ELFFrontPanel.class.getResourceAsStream(f);
			if (ttf != null) {
				dspFont = Font.createFont(Font.TRUETYPE_FONT, ttf);
				dspFont = dspFont.deriveFont(fz);
			}
		} catch (Exception ee) {
			ee.printStackTrace();
		}
		if (dspFont == null) {
			System.err.format("No font %s\n", f);
		}
	}

	private void configElf(Properties props) {
		Color bg = new Color(50, 50, 50);
		pcb = phenolic;
		width = 420;

		boolean auto = (props.getProperty("autorun") != null);
		btns = new JCheckBox[12];
		if (aux_disp) {
			disp = new JLabel[4];
		} else {
			disp = new JLabel[2];
		}
		Icon sw_w_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_on.png"));
		Icon sw_w_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_off.png"));
		Icon sw_r_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_red_on.png"));
		Icon sw_r_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_red_off.png"));
		Icon pb_r_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/pb_on.png"));
		Icon pb_r_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/pb_off.png"));
		qLed = new RoundLED(LED.Colors.RED);
		in = new JButton();
		in.setPreferredSize(new Dimension(50, 30));
		in.addMouseListener(this);
		in.addActionListener(this);
		in.setFocusable(false);
		in.setFocusPainted(false);
		in.setBorderPainted(false);
		if (props.getProperty("elffrontpanel_in_toggle") != null) {
			in.setPressedIcon(sw_r_on);
			in.setIcon(sw_r_off);
		} else {
			in.setPressedIcon(pb_r_on);
			in.setIcon(pb_r_off);
		}
		in.setOpaque(false);
		in.setBackground(bg);
		in.setContentAreaFilled(false);
		in.setMnemonic(IN + 0x1000);
		// 0-7 are data bits, ...
		for (int x = 0; x < 11; ++x) {
			btns[x] = new JCheckBox();
			btns[x].setPreferredSize(new Dimension(50, 30));
			btns[x].setHorizontalAlignment(SwingConstants.CENTER);
			btns[x].setFocusable(false);
			btns[x].setFocusPainted(false);
			btns[x].setBorderPainted(false);
			btns[x].setSelectedIcon(sw_w_on);
			btns[x].setIcon(sw_w_off);
			btns[x].setOpaque(false);
			btns[x].setBackground(bg);
			btns[x].setContentAreaFilled(false);
			btns[x].setMnemonic(x + 0x1000);
			//btns[x].setText(btx[x]);
		}
		gb = new GridBagLayout();
		setLayout(gb);
		setOpaque(false);
		setBackground(bg);
		setOpaque(true);
		gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;

		JPanel pan;
		gc.gridy = 4;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 6;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 9;
		gc.gridx = 9;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);

		// [IN] button
		gc.gridy = 5;
		gc.gridx = 1;
		gb.setConstraints(in, gc);
		add(in);
		// [LOAD] button
		gc.gridy = 5;
		gc.gridx = 2;
		if (!auto) {
			btns[LOAD].setSelected(true);
		}
		btns[LOAD].addMouseListener(this);
		gb.setConstraints(btns[LOAD], gc);
		add(btns[LOAD]);
		// [MP] button
		gc.gridy = 5;
		gc.gridx = 7;
		btns[MP].setSelectedIcon(sw_r_on);
		btns[MP].setIcon(sw_r_off);
		btns[MP].addMouseListener(this); // not used
		gb.setConstraints(btns[MP], gc);
		add(btns[MP]);
		// [RUN] button
		gc.gridy = 5;
		gc.gridx = 8;
		if (auto) {
			btns[RUN].setSelected(true);
		}
		btns[RUN].addMouseListener(this);
		gb.setConstraints(btns[RUN], gc);
		add(btns[RUN]);
		// DATA buttons
		gc.gridy = 7;
		gc.gridx = 1;
		for (int x = 7; x >= 0; --x) {
			if ((x & 3) == 0) {
				btns[x].setSelectedIcon(sw_r_on);
				btns[x].setIcon(sw_r_off);
			}
			gb.setConstraints(btns[x], gc);
			add(btns[x]);
			++gc.gridx;
		}
		// Button Labels
		JLabel lab = getLabel("IN");
		gc.gridy = 4;
		gc.gridx = 1;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("LOAD");
		gc.gridy = 4;
		gc.gridx = 2;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("MP");
		gc.gridy = 4;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("RUN");
		gc.gridy = 4;
		gc.gridx = 8;
		gb.setConstraints(lab, gc);
		add(lab);

		// Data buttons labels
		gc.gridy = 6;
		gc.gridx = 1;
		for (int x = 7; x >= 0; --x) {
			lab = getLabel(String.format("%d", x));
			gb.setConstraints(lab, gc);
			add(lab);
			++gc.gridx;
		}
		// Q LED
		pan = getQLED();
		gc.gridy = 1;
		gc.gridx = 0;
		gc.gridwidth = 10;
		gb.setConstraints(pan, gc);
		add(pan);

		pan = getHexDisplay();
		gc.gridy = 2;
		gc.gridx = 0;
		gc.gridwidth = 10;
		gb.setConstraints(pan, gc);
		add(pan);

		// If ROM...
		if (props.getProperty("prom") != null) {
			pan = getROMJumper();
			gc.gridy = 3;
			gc.gridx = 0;
			gc.gridwidth = 10;
			gb.setConstraints(pan, gc);
			add(pan);
			if (auto) {
				btns[PROM].setSelected(true);
			}
			intr.setSwitch(PROM, btns[PROM].isSelected());
		}
	}

	private void configElf2(Properties props) {
		pcb = soldermask;
		Color bg = pcb;
		width = 460;

		boolean encased = (props.getProperty("elf2_case") != null);
		if (encased) {
			bg = pcb = cased;
		}
		boolean auto = (props.getProperty("autorun") != null);
		aux_disp = false;
		btns = new JCheckBox[12];
		disp = new JLabel[2];
		Icon sw_w_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_on.png"));
		Icon sw_w_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_off.png"));
		props.setProperty("hexkeypad_elf2", "yes");
		kpd = new HexKeyPad(props, intr);
		qLed = new RoundLED(LED.Colors.RED);
		in = kpd.getInBtn();
		in.addMouseListener(this);
		in.addActionListener(this);
		in.setMnemonic(IN + 0x1000);
		// 0-7 are data bits, ... not used here
		for (int x = LOAD; x < 11; ++x) {
			btns[x] = new JCheckBox();
			btns[x].setPreferredSize(new Dimension(50, 30));
			btns[x].setHorizontalAlignment(SwingConstants.CENTER);
			btns[x].setFocusable(false);
			btns[x].setFocusPainted(false);
			btns[x].setBorderPainted(false);
			btns[x].setSelectedIcon(sw_w_on);
			btns[x].setIcon(sw_w_off);
			btns[x].setOpaque(false);
			btns[x].setBackground(bg);
			btns[x].setContentAreaFilled(false);
			btns[x].setMnemonic(x + 0x1000);
			//btns[x].setText(btx[x]);
		}
		gb = new GridBagLayout();
		setLayout(gb);
		setOpaque(false);
		setBackground(bg);
		setOpaque(true);
		gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;

		JPanel pan;
		JLabel lab;
		if (encased) {
			gc.gridy = 0;
			gc.gridx = 0;
			pan = new JPanel();
			pan.setPreferredSize(new Dimension(10, 10));
			pan.setOpaque(false);
			gb.setConstraints(pan, gc);
			add(pan);
			lab = getLabel("DISPLAY");
			lab.setPreferredSize(new Dimension(75, 20));
			gc.gridy = 1;
			gc.gridx = 4;
			gc.gridwidth = 1;
			gb.setConstraints(lab, gc);
			add(lab);
			gc.gridwidth = 1;
			lab = getLabel("Q");
			lab.setPreferredSize(new Dimension(25, 20));
			gc.gridy = 1;
			gc.gridx = 5;
			gb.setConstraints(lab, gc);
			add(lab);
		}
		gc.gridy = 4;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 9;
		gc.gridx = 9;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		// misc spacing
		gc.gridy = 2;
		gc.gridx = 1;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(20, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 2;
		gc.gridx = 2;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(50, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 2;
		gc.gridx = 3;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(50, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 2;
		gc.gridx = 8;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(50, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);

		// [IN] button
		gc.gridy = 8;
		gc.gridx = 6;
		gc.gridwidth = 2;
		gb.setConstraints(in, gc);
		add(in);
		gc.gridwidth = 1;
		// [LOAD] button
		gc.gridy = 6;
		gc.gridx = 6;
		if (!auto) {
			btns[LOAD].setSelected(true);
		}
		btns[LOAD].addMouseListener(this);
		gb.setConstraints(btns[LOAD], gc);
		add(btns[LOAD]);
		// [MP] button
		gc.gridy = 7;
		gc.gridx = 6;
		btns[MP].setSelectedIcon(sw_w_on);
		btns[MP].setIcon(sw_w_off);
		btns[MP].addMouseListener(this); // not used
		gb.setConstraints(btns[MP], gc);
		add(btns[MP]);
		// [RUN] button
		gc.gridy = 5;
		gc.gridx = 6;
		if (auto) {
			btns[RUN].setSelected(true);
		}
		btns[RUN].addMouseListener(this);
		gb.setConstraints(btns[RUN], gc);
		add(btns[RUN]);
		// Button Labels
		lab = getLabel("LOAD");
		lab.setPreferredSize(new Dimension(50, 50));
		gc.gridy = 6;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("MP");
		lab.setPreferredSize(new Dimension(50, 50));
		gc.gridy = 7;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("RUN");
		lab.setPreferredSize(new Dimension(50, 50));
		gc.gridy = 5;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);

		pan = kpd.getKeyPad();
		gc.gridy = 5;
		gc.gridx = 2;
		gc.gridwidth = 4;
		gc.gridheight = 4;
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridheight = 1;
		// Q LED
		pan = getQLED();
		if (encased) {
			pan.setPreferredSize(new Dimension(25, 70));
			pan.setBackground(wdw);
			pan.setOpaque(true);
		} else {
			pan.setPreferredSize(new Dimension(20, 20));
		}
		gc.gridy = 2;
		gc.gridx = 5;
		gc.gridwidth = 1;
		gb.setConstraints(pan, gc);
		add(pan);

		pan = getHexDisplay();
		pan.setPreferredSize(new Dimension(75, 70));
		if (encased) {
			pan.setBackground(wdw);
			pan.setOpaque(true);
		}
		gc.gridy = 2;
		gc.gridx = 4;
		gc.gridwidth = 1;
		gb.setConstraints(pan, gc);
		add(pan);

		// If ROM...
		if (props.getProperty("prom") != null) {
			pan = getROMJumper();
			pan.setPreferredSize(new Dimension(85, 20));
			gc.gridy = 2;
			gc.gridx = 6;
			gc.gridwidth = 2;
			gb.setConstraints(pan, gc);
			add(pan);
			if (auto) {
				btns[PROM].setSelected(true);
			}
			intr.setSwitch(PROM, btns[PROM].isSelected());
		}
	}

	private JPanel getROMJumper() {
		Icon jp_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/jp2_lr.png"));
		Icon jp_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/jp_lr.png"));
		btns[PROM] = new JCheckBox();
		btns[PROM].setPreferredSize(new Dimension(25, 15));
		btns[PROM].setHorizontalAlignment(SwingConstants.CENTER);
		btns[PROM].setFocusable(false);
		btns[PROM].setFocusPainted(false);
		btns[PROM].setBorderPainted(false);
		btns[PROM].setSelectedIcon(jp_on);
		btns[PROM].setIcon(jp_off);
		btns[PROM].setOpaque(false);
		btns[PROM].setBackground(pcb);
		btns[PROM].setContentAreaFilled(false);
		btns[PROM].setMnemonic(PROM + 0x1000);
		btns[PROM].addMouseListener(this);
		JPanel pan = new JPanel();
		pan.setBackground(pcb);
		pan.setOpaque(true);
		pan.setPreferredSize(new Dimension(width, 20));
		pan.add(btns[PROM]);
		pan.add(new JLabel("PROM"));
		return pan;
	}

	private JPanel getQLED() {
		JPanel pan = new JPanel();
		pan.setLayout(new GridBagLayout());
		pan.setBackground(pcb);
		pan.setOpaque(true);
		pan.setPreferredSize(new Dimension(width, 20));
		pan.add(qLed, new GridBagConstraints());
		return pan;
	}

	private JPanel getHexDisplay() {
		JPanel pan = new JPanel();
		pan.setLayout(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = 0;
		pan.setBackground(pcb);
		pan.setOpaque(true);
		pan.setPreferredSize(new Dimension(width, 70));
		disp[0] = getDisplay();
		pan.add(disp[0], gc);
		++gc.gridx;
		JPanel pn = new JPanel();
		pn.setPreferredSize(new Dimension(5, 5));
		pn.setOpaque(false);
		pan.add(pn, gc);
		++gc.gridx;
		disp[1] = getDisplay();
		pan.add(disp[1], gc);
		if (aux_disp) {
			++gc.gridx;
			pn = new JPanel();
			pn.setPreferredSize(new Dimension(5, 5));
			pn.setOpaque(false);
			pan.add(pn, gc);
			++gc.gridx;
			disp[2] = getDisplay();
			pan.add(disp[2], gc);
			++gc.gridx;
			pn = new JPanel();
			pn.setPreferredSize(new Dimension(5, 5));
			pn.setOpaque(false);
			pan.add(pn, gc);
			++gc.gridx;
			disp[3] = getDisplay();
			pan.add(disp[3], gc);
		}
		return pan;
	}

	private JLabel getLabel(String txt) {
		JLabel lab = new JLabel("<HTML><CENTER>" + txt + "</CENTER></HTML>");
		lab.setPreferredSize(new Dimension(50, 20));
		lab.setHorizontalAlignment(SwingConstants.CENTER);
		lab.setForeground(Color.white);
		return lab;
	}

	private JLabel getDisplay() {
		JLabel dsp = new JLabel("@");
		dsp.setFont(dspFont);
		dsp.setForeground(Color.red);
		dsp.setBackground(wdw);
		dsp.setOpaque(true);
		dsp.setPreferredSize(new Dimension(fw, 50));
		return dsp;
	}

	// QListener
	public void setQ(boolean on) {
		qLed.set(on);
		qLed.repaint();
	}

	public void setDisplay(int v) {
		char[] c = new char[1];
		c[0] = (char)(((v >> 4) & 0x0f) + '@');
		disp[0].setText(new String(c));
		c[0] = (char)((v & 0x0f) + '@');
		disp[1].setText(new String(c));
		disp[0].repaint();
		disp[1].repaint();
	}

	public void setAuxDisplay(int v) {
		char[] c = new char[1];
		c[0] = (char)(((v >> 4) & 0x0f) + '@');
		disp[2].setText(new String(c));
		c[0] = (char)((v & 0x0f) + '@');
		disp[3].setText(new String(c));
		disp[2].repaint();
		disp[3].repaint();
	}

	private int getData() {
		int v = 0;
		if (elf2) {
			v = kpd.in(kpd.getBaseAddress());
		} else {
			for (int x = 7; x >= 0; --x) {
				v <<= 1;
				if (btns[x].isSelected()) v |= 1;
			}
		}
		return v;
	}

	public boolean getMP() { return btns[9].isSelected(); }
	public boolean getLOAD() { return btns[8].isSelected(); }

	// DMAController
	public boolean isActive(boolean in) {
		return in && input;
	}
	public int readDataBus() {
		input = false;
		intr.lowerDMA_IN(src);
		int val = getData();
		return val;
	}
	public void writeDataBus(int val) { }

	// IODevice
	public void reset() {}
	public int getBaseAddress() { return ioa; }
	public int getMask() { return iom; }
	public int getDevType() { return IODevice.IN_OUT; }
	public int in(int port) {
		return getData();
	}
	public void out(int port, int value) {
		if (!aux_disp || port == 4) {
			setDisplay(value);
		} else if (aux_disp && port == 7) {
			setAuxDisplay(value);
		}
	}
	public String getDeviceName() { return "ELF-FP"; }
	public String dumpDebug() {
		String ret = String.format("port %d mask %d aux=%s\n",
				ioa, iom, aux_disp);
		return ret;
	}

	// ActionListener
	// For remote operation of IN button...
	// TODO: simulate EFn for some period...
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof JButton) {
			JButton b = (JButton)e.getSource();
			int k = b.getMnemonic() & 0xff;
			if (k != IN) return;
			if (btns[LOAD].isSelected()) {
				input = true;
				intr.raiseDMA_IN(src);
			}
		}
	}

	// MouseListener
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		// NOTE: JCheckBoxes have not changed state yet...
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		switch (mn) {
		case IN:
			intr.setEF(src, efn, true);
			break;
		}
	}
	public void mouseReleased(MouseEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		switch (mn) {
		case LOAD:
		case RUN:
		case PROM:
			intr.setSwitch(mn, btns[mn].isSelected());
			break;
		case IN:
			intr.setEF(src, efn, false);
			break;
		}
	}
}
