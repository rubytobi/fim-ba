package Util;

import java.util.GregorianCalendar;
import java.util.Hashtable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.sun.research.ws.wadl.Application;

public class SimulationFridge {
	@JsonView(View.Summary.class)
	private boolean doorOpen;

	@JsonView(View.Detail.class)
	private Hashtable<String, double[]> schedule;

	public SimulationFridge() {
		doorOpen = false;
		schedule = new Hashtable<String, double[]>();
	}

	@JsonIgnoreProperties
	public void addNewValues(String s, double[] values) {
		schedule.put(s, values);
	}

	@JsonIgnoreProperties
	private void openDoor() {
		int zufall = (int) (Math.random() * 60);
		if (zufall == 30) {
			doorOpen = true;
		}
	}

	@JsonIgnoreProperties
	private void closeDoor() {
		int zufall = (int) (Math.random() * 3);
		if (zufall == 1) {
			doorOpen = false;
		}
	}

	// Gibt zufällig einen um Math.random() vom Plan abweichenden Wert oder den
	// geplanten Wert zurück
	@JsonIgnoreProperties
	public double getTemperature(GregorianCalendar time) {
		if (start.Application.Params.enableDeltaLoadprofiles) {
			double change = 0;
			openDoor();
			if (doorOpen) {
				change = Math.random();
			}
			closeDoor();
			return schedule.get(DateTime.ToString(time))[1] + change;
		} else {
			return schedule.get(DateTime.ToString(time))[1];
		}
	}

	// Gibt den Wert mit Abweichung change vom Plan zurück
	@JsonIgnoreProperties
	public double getChange(GregorianCalendar time, double change) {
		return schedule.get(DateTime.ToString(time))[1] + change;
	}
}
