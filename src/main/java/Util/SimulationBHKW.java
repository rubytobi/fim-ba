package Util;

import java.util.GregorianCalendar;
import java.util.TreeMap;
import java.util.Calendar;

import Util.DateTime;


public class SimulationBHKW {
	private TreeMap<String, double[][]> allSchedules = new TreeMap<String, double[][]>();
	
	public SimulationBHKW() {
		
	}
	
	public double[][] getNewSchedule(GregorianCalendar start) {
		return allSchedules.get(DateTime.ToString(start));
	}
	
	public double getPower(GregorianCalendar time) {
		int slot = (int) Math.floor(time.get(Calendar.MINUTE)/15);
		time.set(Calendar.MINUTE, 0);
		double[] planned = allSchedules.get(DateTime.ToString(time))[slot];
		return planned[1];
	}
}
