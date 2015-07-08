package Packet;

import java.util.Date;
import java.util.GregorianCalendar;

import Util.DateTime;

public class DeviceLoadprofile {
	private Date date;
	private double[] values;

	public DeviceLoadprofile() {
		// dummy constructor
	}

	public DeviceLoadprofile(Date datetime, double[] loadprofile) {
		this.values = loadprofile;
		this.date = datetime;
	}

	public double[] getValues() {
		return values;
	}

	public Date getDate() {
		return date;
	}
}
