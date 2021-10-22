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

		front_end = new JFrame("Virtual COS/MAC ELF Computer");
		front_end.getContentPane().setName("COS/MAC ELF Emulator");
		front_end.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		front_end.getContentPane().setBackground(new Color(100, 100, 100));
		front_end.setFocusTraversalKeysEnabled(false);
		front_end.setFocusable(true);

		COSMAC_ELF elf = new COSMAC_ELF(props, null); // may add 'screen'...
		front_end.add(elf.getFrontPanel());
		if (elf.getFrontPanel().keyListener() != null) {
			front_end.addKeyListener(elf.getFrontPanel().keyListener());
		}
		ELFOperator op = new ELFOperator(front_end, props);
		op.setCommander(elf.getCommander());

		front_end.pack();
		front_end.setLocationByPlatform(true);
		front_end.setVisible(true);

		elf.reset();
		elf.start(true); // spawns its own thread... returns immediately
	}
}
