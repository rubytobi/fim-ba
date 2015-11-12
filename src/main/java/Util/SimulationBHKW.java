package Util;

import java.util.GregorianCalendar;
import java.util.TreeMap;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonView;

import java.util.Calendar;

import Util.DateTime;

public class SimulationBHKW {
	@JsonView(View.Detail.class)
	private TreeMap<String, double[][]> allSchedules = new TreeMap<String, double[][]>();

	@JsonIgnore
	private int numSlots = 4;
	
	@JsonIgnore
	private GregorianCalendar firstStart;

	public SimulationBHKW(double maxLoad) {
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		firstStart = now;

		double[][] schedule1 = new double[2][numSlots * 15];
		double[][] schedule2 = new double[2][numSlots * 15];
		boolean rising = true;
		double value = maxLoad;
		int rounds = 0;
		for (int i = 0; i < numSlots * 15; i++) {
			schedule1[0][i] = 5;
			schedule1[1][i] = value;
			schedule2[0][i] = 5;
			schedule2[1][i] = maxLoad - value;
			if (value == maxLoad || value == 10) {
				rising = !rising;
			}
			if (rounds == 10) {
				rounds = 0;
				if (rising) {
					value++;
				} else {
					value--;
				}
			} else {
				rounds++;
			}

		}
		allSchedules.put(DateTime.ToString(now), schedule1);
		for (int i=0; i<24; i++) {
			now.add(Calendar.HOUR_OF_DAY, 1);
			allSchedules.put(DateTime.ToString(now), schedule2);
		}
	}

	public double[][] getNewSchedule(GregorianCalendar start) {
		double[][] getSchedule =  allSchedules.get(DateTime.ToString(start));
		if (getSchedule == null) {
			GregorianCalendar help = (GregorianCalendar) firstStart.clone();
			help.set(Calendar.HOUR_OF_DAY, start.get(Calendar.HOUR_OF_DAY));
			getSchedule = allSchedules.get(DateTime.ToString(help));
		}
		if (getSchedule == null) {
			Set<String> set = allSchedules.keySet();
			for (String date: set) {
				getSchedule = allSchedules.get(date);
				break;
			}
		}
		return getSchedule;
	}

	public double getPower(GregorianCalendar time) {
		int minute = time.get(Calendar.MINUTE) / 15;
		time.set(Calendar.MINUTE, 0);
		double planned = allSchedules.get(DateTime.ToString(time))[1][minute];
		return planned;
	}
}
