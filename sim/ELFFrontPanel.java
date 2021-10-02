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

public class ELFFrontPanel extends JPanel
		implements DMAController, MouseListener {
	private Font tiny;
	private Font lesstiny;
	private Color wdw = new Color(70, 0, 0);

	JButton[] btns;
	GridBagLayout gb;
	GridBagConstraints gc;

	public ELFFrontPanel(Properties props) {
		super();
		tiny = new Font("Sans-serif", Font.PLAIN, 8);
		lesstiny = new Font("Sans-serif", Font.PLAIN, 10);
		btns = new JButton[16];
		Border lb = BorderFactory.createBevelBorder(BevelBorder.RAISED);
		Color bg = new Color(50, 50, 50);
		for (int x = 0; x < 16; ++x) {
			btns[x] = new JButton();
			btns[x].setPreferredSize(new Dimension(50, 50));
			btns[x].setBackground(bg);
			btns[x].setForeground(Color.white);
			btns[x].setBorder(lb);
			btns[x].setFocusPainted(false);
			btns[x].setPressedIcon(null);
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
		gc.gridx = 1;
		gc.gridy = 1;
		++gc.gridy;
		--gc.gridy;
		gc.gridx += 3;
		++gc.gridx;
		gc.gridy += 2;
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		gb.setConstraints(pan, gc);
		add(pan);
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
