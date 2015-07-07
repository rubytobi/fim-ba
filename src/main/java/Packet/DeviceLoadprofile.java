package Packet;

import java.util.Date;
import java.util.GregorianCalendar;

public class DeviceLoadprofile {
	private double[] values;
	private GregorianCalendar calendar;

	public DeviceLoadprofile(GregorianCalendar calendar, double[] loadprofile) {
		this.values = loadprofile;
		this.calendar = calendar;
	}

	public double[] getValues() {
		return values;
	}

	public Date getDate() {
		return calendar.getTime();
	}
}
