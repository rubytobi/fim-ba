package Util;

import java.util.GregorianCalendar;
import java.util.Hashtable;


public class SimulationFridge {
	private Hashtable<String, double[]> schedule; 
	private boolean doorOpen;
	
	public SimulationFridge () {
		doorOpen = false;
		schedule = new Hashtable<String, double[]>();
	}
	
	public void addNewValues(String s, double[] values) {
		schedule.put(s, values);
	}
	
	private void openDoor() {
		int zufall = (int) (Math.random()*60);
		if (zufall == 30) {
			doorOpen = true;
		}
	}
	
	private void closeDoor() {
		int zufall = (int) (Math.random()*3);
		if (zufall == 1) {
			doorOpen = false;
		}
	}
	
	// Gibt zufällig einen um Math.random() vom Plan abweichenden Wert oder den geplanten Wert zurück
	public double getTemperature(GregorianCalendar time) {
		double change = 0;
		openDoor();
		if (doorOpen) {
			change = Math.random();
		}
		closeDoor();
		return schedule.get(DateTime.ToString(time))[1]+change;
	}
	
	// Gibt den Wert mit Abweichung change vom Plan zurück
	public double getChange(GregorianCalendar time, double change) {
		return schedule.get(DateTime.ToString(time))[1]+change;
	}
}
