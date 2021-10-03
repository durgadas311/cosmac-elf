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

//    0   1    2    3     4     5     6      7    8     9
// 0: +-------------------------------------------------+
// 1: |                [DISP] [DISP]                    |
// 2: |                                                 |
// 3: | <IN> [LOAD]                       [MP]   [RUN]  |
// 4: |                                                 |
// 5: | [7]  [6]   [5]   [4]   [3]   [2]   [1]   [0]    |
// 6: +-------------------------------------------------+
//
// <..> = momentary switch

public class ELFFrontPanel extends JPanel
		implements IODevice, DMAController, MouseListener {
	static final int LOAD = 8;	// index/id of LOAD switch
	static final int MP = 9;	// index/id of MP switch
	static final int RUN = 10;	// index/id of RUN switch
	static final int IN = 12;	// index/id of IN switch
	private Font tiny;
	private Font lesstiny;
	private Font til311;
	private Color wdw = new Color(70, 0, 0);
	private boolean input = false;
	private Interruptor intr;
	private int src;

	JCheckBox[] btns;
	JButton in;
	JLabel[] disp;
	GridBagLayout gb;
	GridBagConstraints gc;
	LED qLed;

	public ELFFrontPanel(Properties props, Interruptor intr) {
		super();
		this.intr = intr;
		src = intr.registerINT();
		intr.addDMAController(this);
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		lesstiny = new Font("Sans-serif", Font.PLAIN, 10);
		btns = new JCheckBox[11];
		disp = new JLabel[2];
		Border lb = BorderFactory.createBevelBorder(BevelBorder.RAISED);
		Color bg = new Color(50, 50, 50);
		Icon sw_w_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_on.png"));
		Icon sw_w_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_off.png"));
		Icon sw_r_on = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_red_on.png"));
		Icon sw_r_off = new ImageIcon(ELFFrontPanel.class.getResource("icons/toggle_red_off.png"));
		String f = "TIL311.ttf";
		float fz = 35f;
		try {
			java.io.InputStream ttf;
			ttf = ELFFrontPanel.class.getResourceAsStream(f);
			if (ttf != null) {
				til311 = Font.createFont(Font.TRUETYPE_FONT, ttf);
				til311 = til311.deriveFont(fz);
			}
		} catch (Exception ee) {
			ee.printStackTrace();
		}
		if (til311 == null) {
			System.err.format("No TIL311\n");
		}
		qLed = new RoundLED(LED.Colors.RED);
		in = new JButton();
		in.setPreferredSize(new Dimension(50, 30));
		in.addMouseListener(this);
		in.setFocusable(false);
		in.setFocusPainted(false);
		in.setBorderPainted(false);
		in.setPressedIcon(sw_r_on);
		in.setIcon(sw_r_off);
		in.setOpaque(false);
		in.setBackground(bg);
		in.setContentAreaFilled(false);
		in.setMnemonic(0x100c);
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
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 2;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 4;
		gc.gridx = 0;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		gc.gridy = 6;
		gc.gridx = 9;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);

		gc.gridy = 1;
		gc.gridx = 1;
		// TODO: add TIL311 displays...
		// [IN] button
		gc.gridy = 3;
		gc.gridx = 1;
		gb.setConstraints(in, gc);
		add(in);
		// [LOAD] button
		gc.gridy = 3;
		gc.gridx = 2;
		btns[LOAD].setSelected(true);
		btns[LOAD].addMouseListener(this);
		gb.setConstraints(btns[LOAD], gc);
		add(btns[LOAD]);
		// [MP] button
		gc.gridy = 3;
		gc.gridx = 7;
		btns[MP].setSelectedIcon(sw_r_on);
		btns[MP].setIcon(sw_r_off);
		btns[MP].addMouseListener(this); // not used
		gb.setConstraints(btns[MP], gc);
		add(btns[MP]);
		// [RUN] button
		gc.gridy = 3;
		gc.gridx = 8;
		btns[RUN].addMouseListener(this);
		gb.setConstraints(btns[RUN], gc);
		add(btns[RUN]);
		// DATA buttons
		gc.gridy = 5;
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
		gc.gridy = 2;
		gc.gridx = 1;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("LOAD");
		gc.gridy = 2;
		gc.gridx = 2;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("MP");
		gc.gridy = 2;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = getLabel("RUN");
		gc.gridy = 2;
		gc.gridx = 8;
		gb.setConstraints(lab, gc);
		add(lab);

		// Data buttons labels
		gc.gridy = 4;
		gc.gridx = 1;
		for (int x = 7; x >= 0; --x) {
			lab = getLabel(String.format("%d", x));
			gb.setConstraints(lab, gc);
			add(lab);
			++gc.gridx;
		}

		gc.gridy = 1;
		gc.gridx = 4;
		gc.gridheight = 3;
		disp[0] = getDisplay();
		gb.setConstraints(disp[0], gc);
		add(disp[0]);
		gc.gridy = 1;
		gc.gridx = 5;
		gc.gridheight = 3;
		disp[1] = getDisplay();
		gb.setConstraints(disp[1], gc);
		add(disp[1]);
		gc.gridy = 1;
		gc.gridx = 3;
		gc.gridheight = 3;
		gb.setConstraints(qLed, gc);
		add(qLed);

		// Now safe to do this?
		intr.setSwitch(RUN, btns[RUN].isSelected());
		intr.setSwitch(LOAD, btns[LOAD].isSelected());
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
		dsp.setFont(til311);
		dsp.setForeground(Color.red);
		dsp.setBackground(wdw);
		dsp.setOpaque(true);
		dsp.setPreferredSize(new Dimension(25, 50));
		return dsp;
	}

	public void setQLed(boolean on) {
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

	private int getData() {
		int v = 0;
		for (int x = 7; x >= 0; --x) {
			v <<= 1;
			if (btns[x].isSelected()) v |= 1;
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
	public int getBaseAddress() { return 0b100; } // N2 only
	public int getMask() { return 0b100; } // N2 only
	public int in(int port) {
		return getData();
	}
	public void out(int port, int value) {
		setDisplay(value);
	}
	public String getDeviceName() { return "ELF-FP"; }
	public String dumpDebug() { return "no debug data, yet"; }

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
			if (btns[LOAD].isSelected()) {
				input = true;
				intr.raiseDMA_IN(src);
			}
			intr.setSwitch(IN, true);
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
			intr.setSwitch(mn, btns[mn].isSelected());
			break;
		case IN:
			intr.setSwitch(IN, false);
			break;
		}
	}
}
