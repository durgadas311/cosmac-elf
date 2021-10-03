// Copyright (c) 2018 Douglas Miller <durgadas311@gmail.com>

import java.awt.*;
import javax.swing.*;

class RoundLED extends LED {
	boolean isOn;

	public RoundLED(Colors color) {
		super(color);
		setOpaque(false);
		setPreferredSize(new Dimension(16, 16));
		isOn = false;
	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2d = (Graphics2D)g;
		g2d.addRenderingHints(new RenderingHints(
			RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON));
		//g2d.setColor(Color.black);
		//g2d.fillOval(0, 0, 16, 16);
		if (isOn) {
			g2d.setColor(on);
		} else {
			g2d.setColor(off);
		}
		g2d.fillOval(2, 2, 12, 12);
	}

	public static Dimension getDim() { return new Dimension(16, 16); }

	public boolean is() { return isOn; }
	public void set(boolean onf) {
		if (isOn != onf) {
			isOn = onf;
			repaint();
		}
	}
}
