// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class GlassTtySerial implements SerialDevice,
			KeyListener, ActionListener, Runnable {
	VirtualUART uart;
	String dbg;
	InputStream inp;
	OutputStream out;
	boolean modem = false;
	boolean nodtr = false;
	JFrame frame;
	JTextArea text;
	JScrollPane scroll;
	JButton DC1;
	JButton DC2;
	JButton DC3;
	JButton DC4;
	int eot;
	int eol;
	File last;
	boolean dc1 = false;
	FileInputStream reader = null;
	File last_rdr = null;
	boolean dc2 = false;
	FileOutputStream punch = null;
	File last_pun = null;

	public GlassTtySerial(Properties props, Vector<String> argv, VirtualUART uart) {
		this.uart = uart;
		for (int x = 0; x < argv.size(); ++x) {
			if (argv.get(x).equalsIgnoreCase("modem")) {
				modem = true;
			} else if (argv.get(x).equalsIgnoreCase("nodtr")) {
				nodtr = true;
			}
		}
		last = new File(".");
		dbg = "GlassTtySerial\n";
		uart.attachDevice(this);
		if (!modem) {
			uart.setModem(VirtualUART.SET_CTS |
					VirtualUART.SET_DSR |
					VirtualUART.SET_DCD);
		}
		frame = new JFrame("Glass Teletype");
		// This allows TAB to be sent
		frame.setFocusTraversalKeysEnabled(false);
		frame.addKeyListener(this);
		JMenuBar mb = new JMenuBar();
		JMenu mu = new JMenu("Tape");
		mb.add(mu);
		JMenuItem mi = new JMenuItem("Reader", KeyEvent.VK_R);
		mi.addActionListener(this);
		mu.add(mi);
		mi = new JMenuItem("Punch", KeyEvent.VK_P);
		mi.addActionListener(this);
		mu.add(mi);
		mu = new JMenu("Paper");
		mb.add(mu);
		mi = new JMenuItem("Save", KeyEvent.VK_S);
		mi.addActionListener(this);
		mu.add(mi);
		mi = new JMenuItem("Tear Off", KeyEvent.VK_T);
		mi.addActionListener(this);
		mu.add(mi);
		frame.setJMenuBar(mb);

		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		DC1 = new JButton("XON");
		DC1.setMnemonic(KeyEvent.VK_1);
		DC1.addActionListener(this);
		mb.add(DC1);
		DC3 = new JButton("XOFF");
		DC3.setMnemonic(KeyEvent.VK_3);
		DC3.addActionListener(this);
		mb.add(DC3);
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		DC2 = new JButton("TAPE");
		DC2.setMnemonic(KeyEvent.VK_2);
		DC2.addActionListener(this);
		mb.add(DC2);
		DC4 = new JButton("(TAPE)");
		DC4.setMnemonic(KeyEvent.VK_4);
		DC4.addActionListener(this);
		mb.add(DC4);
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		setDC1(false);
		setDC2(false);
		DC1.setToolTipText("EMPTY");
		DC2.setToolTipText("EMPTY");

		// TODO: cursor
		text = new JTextArea();
		text.setEditable(false);
		text.setFont(new Font("Monospaced", Font.PLAIN, 10));
		text.setLineWrap(true);		// wrap
		text.setWrapStyleWord(false);	// char wrap
		text.setForeground(Color.black);
		text.setBackground(Color.white);
		clear();
		text.addKeyListener(this);
		scroll = new JScrollPane(text);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scroll.setPreferredSize(new Dimension(600, 320));
		frame.add(scroll, BorderLayout.CENTER);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		// Paper Tape Reader runs in thread...
		Thread t = new Thread(this);
		t.start();
	}

	// Paper Tape Reader
	private synchronized void setDC1(boolean on) {
		dc1 = (reader != null && on);
		DC1.setEnabled(reader != null && !dc1);
		DC3.setEnabled(reader != null && dc1);
	}

	// Paper Tape Punch
	private synchronized void setDC2(boolean on) {
		dc2 = (punch != null && on);
		DC2.setEnabled(punch != null && !dc2);
		DC4.setEnabled(punch != null && dc2);
	}

	// SerialDevice interface:
	//
	public void write(int b) {
		if (dc2 && punch != null) {
			try { punch.write(b); } catch (Exception ee) {}
		}
		if (b == 0x11)	{	// DC1 = ^Q = XON (reader ON)
			setDC1(true);
			return;
		}
		if (b == 0x12)	{	// DC2 = ^R (punch ON)
			setDC2(true);
			return;
		}
		if (b == 0x13)	{	// DC3 = ^S = XOFF (reader OFF)
			setDC1(false);
			return;
		}
		if (b == 0x14)	{	// DC4 = ^T (punch OFF)
			setDC2(false);
			try {
				punch.flush();
			} catch (Exception ee) {}
			return;
		}
		if (b >= ' ' || b == 0x0a || b == 0x0d) {
			byte[] bb = { (byte)b };
			text.insert(new String(bb), eot++);
			text.setCaretPosition(eot);
			if (b == 0x0a) ++eol;
			int y = eol * 10;	// font height = 10
			text.scrollRectToVisible(new Rectangle(0, y - 50, 100, 50));
		}
	}

	private void clear() {
		text.setText("\u2588");	// cursor
		eot = 0;
		eol = 1;
	}

	// This should not be used...
	// We push received data from the thread...
	public int read() {
		return -1;
	}

	// Not used...
	public int available() {
		return 0;
	}

	public void rewind() {}

	// This must NOT call uart.setModem() (or me...)
	public void modemChange(VirtualUART me, int mdm) {
	}
	public int dir() { return SerialDevice.DIR_OUT; }

	public String dumpDebug() {
		return "";
	}
	/////////////////////////////

	private void rdrFile() {
		if (reader != null) {
			try { reader.close(); } catch (Exception ee) {}
			reader = null;
			DC1.setToolTipText("EMPTY");
			setDC1(false);
		}
		SuffFileChooser ch = new SuffFileChooser("Reader",
			new String[]{ "txt" },
			new String[]{ "Text files" },
			last_rdr != null ? last_rdr : last, null);
		if (last_rdr != null) {
			ch.setSelectedFile(last_rdr);
		}
		int rv = ch.showDialog(frame);
		if (rv != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File file = ch.getSelectedFile();
			reader = new FileInputStream(file);
			last = file;
			last_rdr = file;
			DC1.setToolTipText(file.getName());
			setDC1(false);
		} catch (Exception ee) {
			ELFOperator.warn(frame, "Reader", ee.toString());
		}
	}

	private void punFile() {
		if (punch != null) {
			try { punch.close(); } catch (Exception ee) {}
			punch = null;
			DC2.setToolTipText("EMPTY");
			setDC2(false);
		}
		SuffFileChooser ch = new SuffFileChooser("Punch",
			new String[]{ "txt" },
			new String[]{ "Text files" },
			last_pun != null ? last_pun : last, null);
		// For punch, don't risk overwriting previous file...
		//if (last_pun != null) {
		//	ch.setSelectedFile(last_pun);
		//}
		int rv = ch.showDialog(frame);
		if (rv != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			File file = ch.getSelectedFile();
			punch = new FileOutputStream(file);
			last = file;
			last_pun = file;
			DC2.setToolTipText(file.getName());
			setDC2(false);
		} catch (Exception ee) {
			ELFOperator.warn(frame, "Punch", ee.toString());
		}
	}

	private void save() {
		SuffFileChooser ch = new SuffFileChooser("Save",
			new String[]{ "txt" },
			new String[]{ "Text files" }, last, null);
		int rv = ch.showDialog(frame);
		if (rv != JFileChooser.APPROVE_OPTION) {
			return;
		}
		try {
			FileOutputStream fo = new FileOutputStream(ch.getSelectedFile());
			fo.write(text.getText(0, eot).getBytes());
			fo.close();
			last = ch.getSelectedFile();
		} catch (Exception ee) {
			ELFOperator.warn(frame, "Save", ee.toString());
		}
	}

	public void keyTyped(KeyEvent e) {
		// e.getKeyCode() is not valid in keyTyped...
	}
	public void keyReleased(KeyEvent e) {
	}
	public void keyPressed(KeyEvent e) {
		e.consume(); // prevent JTextArea from seeing it
		int c = e.getKeyChar();
		int k = e.getKeyCode();
		int m = e.getModifiers();
		if (k == KeyEvent.VK_ENTER) {
			c = '\r';
		}
		if (c >= 0 && c <= 0x7f) {
			uart.put(c, false);
		}
	}

	public void actionPerformed(ActionEvent e) {
		int k = -1;
		if (e.getSource() instanceof JMenuItem) {
			JMenuItem m = (JMenuItem)e.getSource();
			k = m.getMnemonic();
		} else if (e.getSource() instanceof JButton) {
			JButton b = (JButton)e.getSource();
			k = b.getMnemonic();
		} else {
			return;
		}
		switch (k) {
		case KeyEvent.VK_P:
			// TODO: spawn off to thread?
			punFile();
			break;
		case KeyEvent.VK_R:
			// TODO: spawn off to thread?
			rdrFile();
			break;
		case KeyEvent.VK_S:
			// TODO: spawn off to thread?
			save();
			break;
		case KeyEvent.VK_T:
			clear();
			text.repaint();	// needed?
			break;
		case KeyEvent.VK_1:
			setDC1(true);
			break;
		case KeyEvent.VK_2:
			setDC2(true);
			break;
		case KeyEvent.VK_3:
			setDC1(false);
			break;
		case KeyEvent.VK_4:
			setDC2(false);
			break;
		}
	}

	// Paper Tape Reader function...
	public void run() {
		while (true) {
			while (!dc1 || reader == null) {
				try { Thread.sleep(10); } catch (Exception ee) {}
				synchronized(this) {}
			}
			// Seem to need a little time before the flood
			try { Thread.sleep(10); } catch (Exception ee) {}
			while (dc1 && reader != null) {
				try {
					int c = reader.read();
					if (c < 0) {
						setDC1(false);
						break;
					}
					// TODO: suppress LF?
					while (dc1 && !uart.ready()) {
						Thread.sleep(10);
						synchronized(this) {}
					}
					if (dc1) uart.put(c, false);
					// a generous pause after LF
					if (c == 0x0a) {
						Thread.sleep(200);
					}
				} catch (Exception ee) {}
				synchronized(this) {}
			}
		}
	}
}
