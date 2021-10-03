// Copyright (c) 2011,2014 Douglas Miller
// $Id: SuffFileFilter.java,v 1.3 2014/01/14 21:53:51 drmiller Exp $

import java.io.*;

class SuffFileFilter extends javax.swing.filechooser.FileFilter {
	private String _sfx;
	private String _dsc;

	public static String getExtension(File f) {
		String ext = null;
		String s = f.getName();
		int i = s.lastIndexOf('.');

		if (i > 0 &&  i < s.length() - 1) {
			ext = s.substring(i+1);
		}
		return ext;
	}

	public SuffFileFilter(String suffix, String desc) {
		_sfx = suffix;
		_dsc = desc;
	}

	public boolean accept(File f) {
		if (f.isDirectory()) return true;
		String extension = getExtension(f);
		if (extension != null && extension.equalsIgnoreCase(_sfx)) {
			return true;
		}
		return false;
	}

	public String getDescription() {
		return _dsc;
	}

	public String getSuffix() {
		return _sfx;
	}
}
