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
	private double[][] schedule;

	@JsonIgnore
	private int numSlots = 4;

	@JsonIgnore
	private GregorianCalendar firstStart;

	public SimulationBHKW(double maxLoad, double sizeHeatReservoir) {
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		firstStart = now;

		schedule = new double[2][numSlots * 15];
		boolean rising = true;
		double value = maxLoad;
		int rounds = 0;
		double change = Math.round(100.00 * maxLoad * 0.2) / 100.00;
		for (int i = 0; i < numSlots * 15; i++) {
			schedule[0][i] = sizeHeatReservoir * 0.5;
			schedule[1][i] = value;
			if (rounds == 10) {
				if ( (rising & value+change > maxLoad) || (!rising & value-change < maxLoad*0.5)) {
					rising = !rising;
				}
				rounds = 0;
				if (rising) {
					value = Math.round(100.00 * (value+change)) / 100.00;
				} else {
					value = Math.round(100.00 * (value-change)) / 100.00;
				}
			} else {
				rounds++;
			}

		}
	}

	public double[][] getNewSchedule(String start) {
		return schedule;
	}

	public double getPower(GregorianCalendar time) {
		int minute = time.get(Calendar.MINUTE) / 15;
		time.set(Calendar.MINUTE, 0);
		double planned = schedule[1][minute];
		return planned;
	}
}
