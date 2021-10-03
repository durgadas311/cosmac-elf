// Copyright (c) 2017 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;

public class RectLED extends LED {
	boolean isOn;
	public RectLED(Colors color) {
		super(color);
		setBackground(off);
		setPreferredSize(new Dimension(16, 8));
		isOn = false;
	}

	public static Dimension getDim() { return new Dimension(16, 8); }

	public boolean is() { return isOn; }
	public void set(boolean onf) {
		if (isOn == onf) {
			return;
		}
		isOn = onf;
		if (onf) {
			setBackground(on);
		} else {
			setBackground(off);
		}
		repaint();
	}
}
