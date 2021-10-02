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
// 3: | [IN] [LOAD]                       [MP]   [RUN]  |
// 4: |                                                 |
// 5: | [7]  [6]   [5]   [4]   [3]   [2]   [1]   [0]    |
// 6: +-------------------------------------------------+

public class ELFFrontPanel extends JPanel
		implements DMAController, MouseListener {
	private Font tiny;
	private Font lesstiny;
	private Font til311;
	private Color wdw = new Color(70, 0, 0);

	JButton[] btns;
	JLabel[] disp;
	GridBagLayout gb;
	GridBagConstraints gc;

	public ELFFrontPanel(Properties props) {
		super();
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		lesstiny = new Font("Sans-serif", Font.PLAIN, 10);
		btns = new JButton[12];
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
		// 0-7 are data bits, ...
		for (int x = 0; x < 12; ++x) {
			btns[x] = new JButton();
			btns[x].setPreferredSize(new Dimension(50, 30));
			btns[x].setFocusPainted(false);
			btns[x].setBorderPainted(false);
			btns[x].setPressedIcon(sw_w_on);
			btns[x].setIcon(sw_w_off);
			btns[x].setOpaque(false);
			btns[x].setBackground(bg);
			btns[x].setContentAreaFilled(false);
			btns[x].addMouseListener(this);
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
		btns[8].setPressedIcon(sw_r_on);
		btns[8].setIcon(sw_r_off);
		gb.setConstraints(btns[8], gc);
		add(btns[8]);
		// [LOAD] button
		gc.gridy = 3;
		gc.gridx = 2;
		gb.setConstraints(btns[9], gc);
		add(btns[9]);
		// [MP] button
		gc.gridy = 3;
		gc.gridx = 7;
		btns[10].setPressedIcon(sw_r_on);
		btns[10].setIcon(sw_r_off);
		gb.setConstraints(btns[10], gc);
		add(btns[10]);
		// [RUN] button
		gc.gridy = 3;
		gc.gridx = 8;
		gb.setConstraints(btns[11], gc);
		add(btns[11]);
		// DATA buttons
		gc.gridy = 5;
		gc.gridx = 1;
		for (int x = 7; x >= 0; --x) {
			if ((x & 3) == 0) {
				btns[x].setPressedIcon(sw_r_on);
				btns[x].setIcon(sw_r_off);
			}
			gb.setConstraints(btns[x], gc);
			add(btns[x]);
			++gc.gridx;
		}
		// Button Labels
		JLabel lab = new JLabel("IN");
		lab.setForeground(Color.white);
		gc.gridy = 2;
		gc.gridx = 1;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = new JLabel("LOAD");
		lab.setForeground(Color.white);
		gc.gridy = 2;
		gc.gridx = 2;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = new JLabel("MP");
		lab.setForeground(Color.white);
		gc.gridy = 2;
		gc.gridx = 7;
		gb.setConstraints(lab, gc);
		add(lab);
		lab = new JLabel("RUN");
		lab.setForeground(Color.white);
		gc.gridy = 2;
		gc.gridx = 8;
		gb.setConstraints(lab, gc);
		add(lab);

		// Data buttons labels
		gc.gridy = 4;
		gc.gridx = 1;
		for (int x = 7; x >= 0; --x) {
			lab = new JLabel(String.format("%d", x));
			lab.setForeground(Color.white);
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
	}

	private JLabel getDisplay() {
		JLabel dsp = new JLabel("@");
		dsp.setFont(til311);
		dsp.setForeground(Color.red);
		dsp.setBackground(wdw);
		dsp.setOpaque(true);
		dsp.setPreferredSize(new Dimension(40, 50));
		return dsp;
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

	// DMAController
	public boolean isActive(boolean in) { return false; }
	public int readDataBus() { return 0; }
	public void writeDataBus(int val) {}

	// MouseListener
	public void mouseClicked(MouseEvent e) {}
	public void mouseEntered(MouseEvent e) {}
	public void mouseExited(MouseEvent e) {}
	public void mousePressed(MouseEvent e) {}
	public void mouseReleased(MouseEvent e) {}

}
