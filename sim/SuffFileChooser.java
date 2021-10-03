// Copyright (c) 2011,2014 Douglas Miller
// $Id: SuffFileChooser.java,v 1.4 2014/01/14 21:53:51 drmiller Exp $

import java.awt.*;
import java.io.*;
import javax.swing.*;

class SuffFileChooser extends JFileChooser {
	static final long serialVersionUID = 311457692041L;
	public static final int WRITE_PROTECT = 0x01;
	public static final int UNMOUNT = 0x02;
	private String _btn;
	private class TapeProt extends JComponent {
		static final long serialVersionUID = 31170769203L;
		public JCheckBox btn;
		public JCheckBox btn2 = null;
		public TapeProt(String b, int accs) {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			if ((accs & WRITE_PROTECT) != 0) {
				btn = new JCheckBox(b);
				add(btn);
			}
			if ((accs & UNMOUNT) != 0) {
				btn2 = new JCheckBox("Unmount");
				add(btn2);
			}
		}
	}
	private TapeProt _prot;
	public void suffFileChooser(String btn, String[] sfxa, String[] dsca,
			String sfx, String dsc, File dir, int accs) {
		if (sfxa != null && dsca != null) {
			SuffFileFilter f = new SuffFileFilter(sfxa[0], dsca[0]);
			setFileFilter(f);
			for (int i = 1; i < dsca.length; ++i) {
				f = new SuffFileFilter(sfxa[i], dsca[i]);
				addChoosableFileFilter(f);
			}
		} else if (sfx != null && dsc != null) {
			SuffFileFilter f = new SuffFileFilter(sfx, dsc);
			setFileFilter(f);
		}
		_btn = btn;
		setApproveButtonText(btn);
		setApproveButtonToolTipText(btn);
		setDialogTitle(btn);
		setDialogType(JFileChooser.SAVE_DIALOG);
		_prot = new TapeProt("Protect", accs);
		setAccessory(_prot);
	}
	public SuffFileChooser(String btn, String[] sfxa, String[] dsca,
			String sfx, String dsc, File dir, int accs) {
		super(dir);
		suffFileChooser(btn, sfxa, dsca, sfx, dsc, dir, accs);
	}
	public SuffFileChooser(String btn, String[] sfxa, String[] dsca,
			String sfx, String dsc, File dir, boolean unmount) {
		super(dir);
		int accs = WRITE_PROTECT;
		if (unmount) {
			accs |= UNMOUNT;
		}
		suffFileChooser(btn, sfxa, dsca, sfx, dsc, dir, accs);
	}
	public SuffFileChooser(String btn, File dir) {
		super(dir);
		suffFileChooser(btn, null, null, null, null, dir, WRITE_PROTECT);
	}
	public SuffFileChooser(String btn, String sfx, String dsc, File dir) {
		super(dir);
		suffFileChooser(btn, null, null, sfx, dsc, dir, WRITE_PROTECT);
	}
	public SuffFileChooser(String btn, String[] sfx, String[] dsc, File dir) {
		super(dir);
		suffFileChooser(btn, sfx, dsc, null, null, dir, WRITE_PROTECT);
	}
	public int showDialog(Component frame) {
		int rv = super.showDialog(frame, _btn);
		if (rv == JFileChooser.APPROVE_OPTION) {
			javax.swing.filechooser.FileFilter ff = getFileFilter();
			if (ff instanceof SuffFileFilter) {
				SuffFileFilter fi = (SuffFileFilter)getFileFilter();
				String sfx = "." + fi.getSuffix();
				if (getSelectedFile().getName().toLowerCase().endsWith(sfx)) {
					return rv;
				}
				File f = new File(getSelectedFile().getAbsolutePath().concat(sfx));
				setSelectedFile(f);
			}
		}
		return rv;
	}
	public boolean isProtected() {
		return _prot.btn != null ? _prot.btn.isSelected() : false;
	}
	public void setProtected(boolean prot) {
		if (_prot.btn != null) {
			_prot.btn.setSelected(prot);
		}
	}
	public boolean isUnmount() {
		return _prot.btn2 != null ? _prot.btn2.isSelected() : false;
	}
}
