package Packet;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;

import Util.DateTime;
import Util.Log;

public class DeviceLoadprofile {
	private double[] values;
	private GregorianCalendar calendar;

	public DeviceLoadprofile(GregorianCalendar calendar, double[] loadprofile) {
		this.values = loadprofile;
		this.calendar = calendar;
	}

	// TODO getter setter

	public double[] getValues() {
		return values;
	}

	public Date getDate() {
		return calendar.getTime();
	}
}
