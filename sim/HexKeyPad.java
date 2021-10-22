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

// Two styles are supported:
// Default:	as described in PE article
//	Active-scanning using 4-to-16 latch/decoder,
//	Scan one with OUT n, sense key-press on EFn, next...
// "Alt":
//	Direct-read using 74C922 (or 2x8-to-3 priority encoders),
//	Sense key-press on EFn, direct read code with INP n.
// "ElfII":
//	Direct-read using 74C923 and latch for last two keys pressed,
//	direct read code with INP n.
//
// Also a "detached" version for embedding in some other JFrame...

public class HexKeyPad extends JFrame
		implements IODevice, MouseListener, KeyListener, ActionListener {

	private JPanel kpd;
	private JButton[] btns;
	private GridBagLayout gb;
	private GridBagConstraints gc;
	private Interruptor intr;
	private int index = 0;
	private int src;
	private int key = -1;
	private int ioa;
	private int iom;
	private int iot;
	private int efn;
	private String name;
	private boolean alt;
	private boolean elf2;

	public JPanel getKeyPad() { return kpd; }
	public JButton getInBtn() { return btns[16]; }

	public HexKeyPad(Properties props, Interruptor intr) {
		super("ELF Hex Keypad");
		this.intr = intr;
		src = intr.registerINT();
		alt = (props.getProperty("hexkeypad_alt") != null);
		elf2 = (props.getProperty("hexkeypad_elf2") != null);
		if (elf2) {
			name = "ElfII_KPD";
			iot = IODevice.IN;
			efn = 2;	// EF3 - not used
			ioa = 0b100;	// INP 4
			iom = 0b100;
		} else if (alt) {
			name = "altKEYPAD";
			iot = IODevice.IN;
			efn = 2;	// EF3
			ioa = 0b100;	// INP 4
			iom = 0b100;
		} else {
			name = "HEXKEYPAD";
			iot = IODevice.OUT;
			efn = 1;	// EF2
			ioa = 0b010;	// OUT 2
			iom = 0b010;
		}
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
		if (alt) {
			intr.setEF(src, efn, alt);	// "alt" is active low EFn
		}

		btns = new JButton[17];
		Color bg = new Color(0,0,0);
		Color ky = new Color(240,240,220);
		Color tx = new Color(0,0,0);
		Border lb = BorderFactory.createBevelBorder(BevelBorder.RAISED);
		int fz = 24;
		Font ft = new Font("Sans-serif", Font.PLAIN, fz);
		for (int x = 0; x < 17; ++x) {
			btns[x] = new JButton();
			btns[x].setPreferredSize(new Dimension(50, 50));
			btns[x].setBackground(ky);
			btns[x].setForeground(tx);
			btns[x].setBorder(lb);
			btns[x].setFocusPainted(false);
			btns[x].setPressedIcon(null);
			btns[x].setFocusable(false);
			if (x < 16) {
				btns[x].setMnemonic(x + 0x1000);
				btns[x].setFont(ft);
				// TODO: decoder vs. scanned, not ELF2 vs ...
				if (elf2) {
					btns[x].addActionListener(this);
				} else {
					btns[x].addMouseListener(this);
				}
				if (x == 0) {
					btns[x].setText("\u00d8");
				} else {
					btns[x].setText(String.format("%X", x));
				}
			} else {
				// must be IN button...
				btns[x].setText("I");
				btns[x].setFont(new Font("Serif", Font.PLAIN, fz));
			}
		}
		getContentPane().setBackground(bg);
		kpd = new JPanel();
		kpd.setBackground(bg);
		gb = new GridBagLayout();
		kpd.setLayout(gb);
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
		if (!elf2) {
			pan = new JPanel();
			pan.setPreferredSize(new Dimension(20, 20));
			pan.setOpaque(false);
			gb.setConstraints(pan, gc);
			kpd.add(pan);
		}
		++gc.gridx;
		++gc.gridy;

		int ret = gc.gridx;
		gb.setConstraints(btns[12], gc);
		kpd.add(btns[12]);
		++gc.gridx;
		gb.setConstraints(btns[13], gc);
		kpd.add(btns[13]);
		++gc.gridx;
		gb.setConstraints(btns[14], gc);
		kpd.add(btns[14]);
		++gc.gridx;
		gb.setConstraints(btns[15], gc);
		kpd.add(btns[15]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[8], gc);
		kpd.add(btns[8]);
		++gc.gridx;
		gb.setConstraints(btns[9], gc);
		kpd.add(btns[9]);
		++gc.gridx;
		gb.setConstraints(btns[10], gc);
		kpd.add(btns[10]);
		++gc.gridx;
		gb.setConstraints(btns[11], gc);
		kpd.add(btns[11]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[4], gc);
		kpd.add(btns[4]);
		++gc.gridx;
		gb.setConstraints(btns[5], gc);
		kpd.add(btns[5]);
		++gc.gridx;
		gb.setConstraints(btns[6], gc);
		kpd.add(btns[6]);
		++gc.gridx;
		gb.setConstraints(btns[7], gc);
		kpd.add(btns[7]);
		gc.gridx = ret;
		++gc.gridy;
		gb.setConstraints(btns[0], gc);
		kpd.add(btns[0]);
		++gc.gridx;
		gb.setConstraints(btns[1], gc);
		kpd.add(btns[1]);
		++gc.gridx;
		gb.setConstraints(btns[2], gc);
		kpd.add(btns[2]);
		++gc.gridx;
		gb.setConstraints(btns[3], gc);
		kpd.add(btns[3]);
		++gc.gridx;
		++gc.gridy;

		if (!elf2) {
			pan = new JPanel();
			pan.setPreferredSize(new Dimension(20, 20));
			pan.setOpaque(false);
			gb.setConstraints(pan, gc);
			kpd.add(pan);

			add(kpd);
			pack();
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			setLocationByPlatform(true);
			setVisible(true);
		}
		System.err.format("HexKeyPad%s at port %d mask %d EF%d\n",
			elf2 ? "(elf2)" : (alt ? "(alt)" : ""),
			ioa, iom, efn + 1);
	}

	// IODevice
	public void reset() {}
	public int getBaseAddress() { return ioa; }
	public int getMask() { return iom; }
	public int getDevType() { return iot; }
	// Only called for "alt" keypad
	public int in(int port) {
		return key & 0xff;
	}
	// Only called for default keypad
	public void out(int port, int value) {
		index = value & 0x0f;
		//System.err.format("index = %d\n", index);
		if (key == index) {
			//System.err.format("EF2* %d\n", index);
			intr.setEF(src, efn, true);
		} else {
			intr.setEF(src, efn, false);
		}
	}
	public String getDeviceName() { return name; }
	public String dumpDebug() {
		String ret = name;
		ret += String.format(" Port %d mask %d\n", ioa, iom);
		return ret;
	}

	// ActionListener
	// Only used for ELF2-style (keyboard-encoder) instances
	public void actionPerformed(ActionEvent e) {
		int k = -1;
		if (e.getSource() instanceof JButton) {
			JButton b = (JButton)e.getSource();
			k = b.getMnemonic();
		} else {
			return;
		}
		key = ((key << 4) | (k & 0x0f)) & 0xff;
	}

	// MouseListener - only for scanned keypads... (not ELF2)
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		//System.err.format("KEY %d\n", mn);
		key = mn;
		if (alt || mn == index) {
			intr.setEF(src, efn, !alt);
		}
	}
	public void mouseReleased(MouseEvent e) {
		AbstractButton btn = (AbstractButton)e.getSource();
		int mn = btn.getMnemonic();
		mn &= 0xff;
		key = -1;
		intr.setEF(src, efn, alt);
	}

	// For now, this only works with ELF2 style
	public void keyTyped(KeyEvent e) {
		// e.getKeyCode() is not valid in keyTyped...
		int c = e.getKeyChar();
		if (c == '\n' || c == 'I' || c == 'i') {
			btns[16].doClick();
		} else if (c >= '0' && c <= '9') {
			btns[c & 0x0f].doClick();
		} else if (c >= 'A' && c <= 'F') {
			btns[10 + (c - 'A')].doClick();
		} else if (c >= 'a' && c <= 'f') {
			btns[10 + (c - 'a')].doClick();
		}
	}
	public void keyReleased(KeyEvent e) {}
	public void keyPressed(KeyEvent e) {}
}
