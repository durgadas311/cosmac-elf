// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>
import java.awt.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;

public class VirtualCOSMAC_ELF {
	private static JFrame front_end;

	public static void main(String[] args) {
		Properties props = new Properties();
		String rc = System.getenv("COSMACELF_CFG");
		for (String arg : args) {
			if (arg.indexOf('=') < 0) {
				File f = new File(arg);
				if (f.exists()) {
					rc = arg;
					//rc = f.getAbsolutePath();
					break;
				}
			}
		}
		if (rc == null) {
			File f = new File("./cosmac_elf.rc");
			if (f.exists()) {
				rc = f.getAbsolutePath();
			}
		}
		if (rc == null) {
			rc = System.getProperty("user.home") + "/.cosmac_elfrc";
		}
		try {
			FileInputStream cfg = new FileInputStream(rc);
			props.load(cfg);
			cfg.close();
			//System.err.format("Using config in %s\n", rc);
			props.setProperty("configuration", rc);
		} catch(Exception ee) {
			//System.err.format("No config file\n");
		}
		// Now override props from file with cmdline
		int x;
		for (String arg : args) {
			if ((x = arg.indexOf('=')) < 0) continue;
			String prop = arg.substring(0, x).trim();
			String val = arg.substring(x + 1).trim();
			props.setProperty(prop, val);
		}

// TODO: optional CDP1861 VDC
//		boolean isH8 = H89.isH8(props);
//		String model = isH8 ? "H8" : "H89";
//
//		CrtScreen screen = new CrtScreen(props);

		front_end = new JFrame("Virtual COS/MAC ELF Computer");
		front_end.getContentPane().setName("COS/MAC ELF Emulator");
		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.getContentPane().setBackground(new Color(100, 100, 100));
//		LEDHandler lh = null;
//		front_end.add(screen);

		COSMAC_ELF elf = new COSMAC_ELF(props, null); // may add 'screen'...
		front_end.add(elf.getFrontPanel());
		// All LEDs should be registered now...
		ELFOperator op = new ELFOperator(front_end, props);
		op.setCommander(elf.getCommander());

		front_end.pack();
		front_end.setVisible(true);

		elf.reset();
		elf.start(); // spawns its own thread... returns immediately
	}
}
