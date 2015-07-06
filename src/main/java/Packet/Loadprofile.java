package Packet;

import java.util.GregorianCalendar;

public class Loadprofile {
	private double[] values;
	private GregorianCalendar calendar;

	public Loadprofile(GregorianCalendar calendar, double[] loadprofile) {
		this.values = loadprofile;
		this.calendar = calendar;
	}
	
	// TODO getter setter

	public double[] getValues() {
		return values;
	}

}
