package Packet;

import java.util.Date;
import java.util.GregorianCalendar;

import Util.DateTime;

public class DeviceLoadprofile {
	private GregorianCalendar date;
	private double[] values;

	public DeviceLoadprofile() {
		// dummy constructor
	}

	public DeviceLoadprofile(GregorianCalendar datetime, double[] loadprofile) {
		this.values = loadprofile;
		this.date = datetime;
	}

	public double[] getValues() {
		return values;
	}

	public GregorianCalendar getDate() {
		return date;
	}
}
