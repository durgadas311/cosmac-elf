// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;
import java.util.Vector;
import java.util.Properties;
import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class TeletypeSerial implements SerialDevice,
			KeyListener, ActionListener, Runnable {
	// ASCII character constants
	static final int cLF = 0x0a;
	static final int cCR = 0x0d;
	static final int cDC1 = 0x11;
	static final int cDC2 = 0x12;
	static final int cDC3 = 0x13;
	static final int cDC4 = 0x14;
	VirtualUART uart;
	String dbg;
	InputStream inp;
	OutputStream out;
	boolean modem = false;
	boolean nodtr = false;
	JFrame frame;
	JTextArea text;
	JScrollPane scroll;
	JButton DC1s;
	JButton DC1;
	JButton DC2;
	JButton DC3;
	JButton DC4;
	boolean auto;
	int eot;
	int eol;
	File last;
	boolean dc1 = false;
	FileInputStream reader = null;
	File last_rdr = null;
	boolean dc2 = false;
	FileOutputStream punch = null;
	File last_pun = null;
	int eol_delay = 0;

	public TeletypeSerial(Properties props, Vector<String> argv, VirtualUART uart) {
		this.uart = uart;
		for (int x = 0; x < argv.size(); ++x) {
			if (argv.get(x).equalsIgnoreCase("modem")) {
				modem = true;
			} else if (argv.get(x).equalsIgnoreCase("nodtr")) {
				nodtr = true;
			}
		}
		String s = props.getProperty("teletype_eol_delay");
		if (s != null) {
			eol_delay = Integer.valueOf(s);
		}
		last = new File(".");
		dbg = "TeletypeSerial\n";
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

		Font ft = new Font("Sans-serif", Font.PLAIN, 12);
		JPanel pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		DC1s = new JButton("START");
		DC1s.setFont(ft);
		DC1s.setPreferredSize(new Dimension(50, 20));
		DC1s.setMargin(new Insets(2, 2, 2, 2));
		DC1s.setMnemonic(KeyEvent.VK_0);
		DC1s.addActionListener(this);
		mb.add(DC1s);
		DC1 = new JButton("AUTO");
		DC1.setFont(ft);
		DC1.setPreferredSize(new Dimension(50, 20));
		DC1.setMargin(new Insets(2, 2, 2, 2));
		DC1.setMnemonic(KeyEvent.VK_1);
		DC1.addActionListener(this);
		mb.add(DC1);
		DC3 = new JButton("STOP");
		DC3.setFont(ft);
		DC3.setPreferredSize(new Dimension(50, 20));
		DC3.setMargin(new Insets(2, 2, 2, 2));
		DC3.setMnemonic(KeyEvent.VK_3);
		DC3.addActionListener(this);
		mb.add(DC3);
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		DC2 = new JButton("TAPE");
		DC2.setFont(ft);
		DC2.setPreferredSize(new Dimension(50, 20));
		DC2.setMargin(new Insets(2, 2, 2, 2));
		DC2.setMnemonic(KeyEvent.VK_2);
		DC2.addActionListener(this);
		mb.add(DC2);
		DC4 = new JButton("(TAPE)");
		DC4.setFont(ft);
		DC4.setPreferredSize(new Dimension(50, 20));
		DC4.setMargin(new Insets(2, 1, 2, 1));
		DC4.setMnemonic(KeyEvent.VK_4);
		DC4.addActionListener(this);
		mb.add(DC4);
		pan = new JPanel();
		pan.setPreferredSize(new Dimension(10, 10));
		pan.setOpaque(false);
		mb.add(pan);
		setAUTO(false);
		setDC1(false);
		setDC2(false);
		DC1.setToolTipText("EMPTY");
		DC2.setToolTipText("EMPTY");
		s = props.getProperty("teletype_reader");
		if (s != null) {
			try {
				setRdrFile(new File(s));
			} catch (Exception ee) {
				System.err.format("teletype_reader: %s\n", ee.toString());
			}
		}
		s = props.getProperty("teletype_auto");
		if (s != null) {
			setAUTO(true);
		}
		s = props.getProperty("teletype_punch");
		if (s != null) {
			try {
				setPunFile(new File(s));
			} catch (Exception ee) {
				System.err.format("teletype_punch: %s\n", ee.toString());
			}
		}

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
		scroll.setPreferredSize(new Dimension(500, 320));
		frame.add(scroll, BorderLayout.CENTER);
		frame.pack();
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setVisible(true);
		// Paper Tape Reader runs in thread...
		Thread t = new Thread(this);
		t.start();
	}

	// Paper Tape Reader
	private synchronized void setAUTO(boolean on) {
		auto = on;
		DC1s.setEnabled(reader != null);
		DC1.setEnabled(reader != null && !auto);
		DC3.setEnabled(reader != null && auto);
	}
	private synchronized void setDC1(boolean on) {
		dc1 = (auto && reader != null && on);
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
		if (b == cDC1)	{	// DC1 = ^Q = XON (reader ON)
			setDC1(true);
			return;
		}
		if (b == cDC2)	{	// DC2 = ^R (punch ON)
			setDC2(true);
			return;
		}
		if (b == cDC3)	{	// DC3 = ^S = XOFF (reader OFF)
			setDC1(false);
			return;
		}
		if (b == cDC4)	{	// DC4 = ^T (punch OFF)
			setDC2(false);
			try {
				punch.flush();
			} catch (Exception ee) {}
			return;
		}
		if (b >= ' ' || b == cLF || b == cCR) {
			byte[] bb = { (byte)b };
			text.insert(new String(bb), eot++);
			text.setCaretPosition(eot);
			if (b == cLF) ++eol;
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

	private void setRdrFile(File rdr) throws Exception {
		reader = new FileInputStream(rdr);
		last = rdr;
		last_rdr = rdr;
		DC1.setToolTipText(rdr.getName());
		setAUTO(false);
		setDC1(false);
	}

	private void rdrFile() {
		if (reader != null) {
			try { reader.close(); } catch (Exception ee) {}
			reader = null;
			DC1.setToolTipText("EMPTY");
			setAUTO(false);
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
			setRdrFile(ch.getSelectedFile());
		} catch (Exception ee) {
			ELFOperator.warn(frame, "Reader", ee.toString());
		}
	}

	private void setPunFile(File pun) throws Exception {
		punch = new FileOutputStream(pun);
		last = pun;
		last_pun = pun;
		DC2.setToolTipText(pun.getName());
		setDC2(false);
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
			setPunFile(ch.getSelectedFile());
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
		case KeyEvent.VK_0:
			setAUTO(true);
			setDC1(true);
			break;
		case KeyEvent.VK_1:
			setAUTO(true);
			break;
		case KeyEvent.VK_2:
			setDC2(true);
			break;
		case KeyEvent.VK_3:
			setAUTO(false);
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
					if (c == cDC3) {
						setDC1(false);
					}
					// A generous pause after LF.
					// This is not needed if the data
					// has sufficient NULs, as does output
					// of LIST command from Tiny BASIC.
					if (c == cLF && eol_delay > 0) {
						Thread.sleep(eol_delay);
					}
				} catch (Exception ee) {}
				synchronized(this) {}
			}
		}
	}
}
