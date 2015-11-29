package Util;

import java.util.GregorianCalendar;
import java.util.Hashtable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;

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
		int zufall = (int) (Math.random() * 120);
		if (zufall == 30) {
			doorOpen = true;
		}
	}

	@JsonIgnoreProperties
	private void closeDoor() {
		int zufall = (int) (Math.random() * 2);
		if (zufall >=1 && zufall < 1.2) {
			doorOpen = false;
		}
	}

	// Gibt zufällig einen um Math.random() vom Plan abweichenden Wert oder den
	// geplanten Wert zurück
	@JsonIgnoreProperties
	public double getTemperature(GregorianCalendar time) {
		double change = 0;
		openDoor();
		if (doorOpen) {
			System.out.println("Tür ist offen");
			change = Math.random();
		}
		closeDoor();
		return schedule.get(DateTime.ToString(time))[1] + change;
	}

	// Gibt den Wert mit Abweichung change vom Plan zurück
	@JsonIgnoreProperties
	public double getChange(GregorianCalendar time, double change) {
		return schedule.get(DateTime.ToString(time))[1] + change;
	}
}
