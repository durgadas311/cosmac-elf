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

public class HexKeyPad extends JFrame
		implements IODevice, MouseListener {

	private JButton[] btns;
	GridBagLayout gb;
	GridBagConstraints gc;
	Interruptor intr;
	private int index = 0;
	private int src;
	private int key = -1;
	private int ioa = 0x010;
	private int iom = 0x010;
	private int efn = 1;

	public HexKeyPad(Properties props, Interruptor intr) {
		super("ELF Hex Keypad");
		this.intr = intr;
		src = intr.registerINT();
		String s = props.getProperty("hexkeypad_port");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 7) {
				System.err.format("Invalid hexkeypad_port: %d\n", n);
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
		s = props.getProperty("hexkeypad_ef");
		if (s != null) {
			int n = Integer.valueOf(s);
			if (n < 1 || n > 4) {
				System.err.format("Invalid hexkeypad_ef: %d\n", n);
			} else {
				efn = n - 1;
			}
		}

		btns = new JButton[16];
		Color bg = new Color(0,0,0);
		Color ky = new Color(240,240,220);
		Color tx = new Color(0,0,0);
		Border lb = BorderFactory.createBevelBorder(BevelBorder.RAISED);
		for (int x = 0; x < 16; ++x) {
			btns[x] = new JButton();
			btns[x].setPreferredSize(new Dimension(50, 50));
			btns[x].setBackground(ky);
			btns[x].setForeground(tx);
			btns[x].setBorder(lb);
			btns[x].setFocusPainted(false);
			btns[x].setPressedIcon(null);
			btns[x].addMouseListener(this);
			btns[x].setMnemonic(x + 0x1000);
			btns[x].setText(String.format("%X", x));
		}
		getContentPane().setBackground(bg);
		gb = new GridBagLayout();
		setLayout(gb);
		gc = new GridBagConstraints();
		gc.fill = GridBagConstraints.NONE;
		gc.gridx = 0;
		gc.gridy = 0;
		gc.weightx = 0;
		gc.weighty = 0;
		gc.gridwidth = 1;
		gc.gridheight = 1;
		gc.anchor = GridBagConstraints.CENTER;

		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(20, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
		++gc.gridx;
		++gc.gridy;

		int ret = gc.gridx;
		gb.setConstraints(btns[12], gc);
		add(btns[12]);
		++gc.gridx;
		gb.setConstraints(btns[13], gc);
		add(btns[13]);
		++gc.gridx;
		gb.setConstraints(btns[14], gc);
		add(btns[14]);
		++gc.gridx;
		gb.setConstraints(btns[15], gc);
		add(btns[15]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[8], gc);
		add(btns[8]);
		++gc.gridx;
		gb.setConstraints(btns[9], gc);
		add(btns[9]);
		++gc.gridx;
		gb.setConstraints(btns[10], gc);
		add(btns[10]);
		++gc.gridx;
		gb.setConstraints(btns[11], gc);
		add(btns[11]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[4], gc);
		add(btns[4]);
		++gc.gridx;
		gb.setConstraints(btns[5], gc);
		add(btns[5]);
		++gc.gridx;
		gb.setConstraints(btns[6], gc);
		add(btns[6]);
		++gc.gridx;
		gb.setConstraints(btns[7], gc);
		add(btns[7]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[0], gc);
		add(btns[0]);
		++gc.gridx;
		gb.setConstraints(btns[1], gc);
		add(btns[1]);
		++gc.gridx;
		gb.setConstraints(btns[2], gc);
		add(btns[2]);
		++gc.gridx;
		gb.setConstraints(btns[3], gc);
		add(btns[3]);
		++gc.gridx;
		++gc.gridy;

		pan = new JPanel();
		pan.setPreferredSize(new Dimension(20, 20));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);

		pack();
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setVisible(true);
	}

	// IODevice
	public void reset() {}
	public int getBaseAddress() { return ioa; }
	public int getMask() { return iom; }
	public int in(int port) { return 0; }
	public void out(int port, int value) {
		index = value & 0x0f;
		//System.err.format("index = %d\n", index);
		if (key == index) {
			//System.err.format("EF2* %d\n", index);
			intr.setEF(src, efn, true);	// EF2
		} else {
			intr.setEF(src, efn, false);	// EF2
		}
	}
	public String getDeviceName() { return "HEXKEYPAD"; }
	public String dumpDebug() { return "no debug data, yet"; }

	// MouseListener
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		//System.err.format("KEY %d\n", mn);
		key = mn;
		if (mn == index) {
			//System.err.format("EF2 %d\n", mn);
			intr.setEF(src, efn, true);	// EF2
		}
	}
	public void mouseReleased(MouseEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		key = -1;
		intr.setEF(src, efn, false);	// EF2
	}
}
