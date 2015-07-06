package start;

import java.util.Calendar;
import java.util.GregorianCalendar;

import Entity.Fridge;

public class Main {
	public static void main (String[] args) {
		Fridge fridge = new Fridge(8, 8.4, 4, 2, -0.5, 0.2, 1, 5);
		GregorianCalendar aenderung = new GregorianCalendar();
		aenderung.set(Calendar.SECOND, 0);
		aenderung.set(Calendar.MILLISECOND, 0);
		aenderung.set(Calendar.MINUTE, 55);
		fridge.sendDeltaLoadprofile(aenderung, 9);
	}
}
