package Util;

import java.util.Date;
import java.text.ParseException;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import start.Application;

import java.util.Calendar;

public class DateTime {
	private static GregorianCalendar programStart = null;

	public static String timestamp() {
		return Application.Params.myDateFormat.format(now().getTime());
	}

	/**
	 * Gibt den String des übergebenen GregorianCalendars zurück.
	 * 
	 * @param calendar
	 *            GregorianCalendar, dessen String erstellt werden soll
	 * @return calendar als String
	 */
	public static String ToString(GregorianCalendar calendar) {
		return Application.Params.myDateFormat.format(calendar.getTime());
	}

	/**
	 * Die Methode gibt immer die aktuelle Zeit der Simulation zurück. Je nach
	 * Belegung der Variable timeFactor kann diese Zeit schneller oder langsamer
	 * als die reale Zeit vergehen.
	 * 
	 * @return Die aktuelle Zeit der Simulation als GregorianCalendar
	 */
	public static GregorianCalendar now() {
		GregorianCalendar realTime = new GregorianCalendar(TimeZone.getTimeZone("Europe/Berlin"));

		if (programStart == null) {
			programStart = realTime;
		}

		GregorianCalendar simulationTime = (GregorianCalendar) realTime.clone();

		// Berechne, wie viele Millisekunden seit dem Start des Programms
		// vergangen sind
		long millisReal = realTime.getTimeInMillis();
		long millisStart = programStart.getTimeInMillis();
		long millis = millisReal - millisStart;

		int millisToAdd = (int) millis;

		millisToAdd *= Application.Params.timeFactor - 1;
		simulationTime.add(Calendar.MILLISECOND, millisToAdd);

		return simulationTime;
	}

	public static GregorianCalendar parse(String calendar) throws IllegalArgumentException {
		GregorianCalendar gc = new GregorianCalendar();
		Date date = null;
		try {
			date = Application.Params.myDateFormat.parse(calendar);
		} catch (ParseException e) {
			throw new IllegalArgumentException();
		}

		gc.setTime(date);

		return gc;
	}

	/**
	 * Gibt zurück, ob der übergebene String
	 * 
	 * @param date
	 *            String das in ein Datum gewandelt werden soll
	 * @return mindestens ein aktuelles GregorianCalendar Object
	 */
	public static GregorianCalendar stringToCalendar(String date) {
		GregorianCalendar calendar = null;

		try {
			calendar = parse(date);
		} catch (IllegalArgumentException e) {
			calendar = now();
		}
		return calendar;
	}

	/**
	 * Gibt die Anfangszeit der aktuellen Stunde zurück.
	 * 
	 * @return Anfangszeit der aktuellen Stunde als GregorianCalendar
	 */
	public static GregorianCalendar currentTimeSlot() {
		GregorianCalendar now = now();

		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		return now;
	}

	/**
	 * Gibt die Anfangszeit der nächsten Stunde zurück.
	 * 
	 * @return Anfangszeit der nächsten Stunde als GregorianCalendar
	 */
	public static GregorianCalendar nextTimeSlot() {
		GregorianCalendar currentTimeSlot = currentTimeSlot();
		currentTimeSlot.add(Calendar.HOUR_OF_DAY, 1);
		return currentTimeSlot;
	}

	public static String set(int what, int count, String time) {
		GregorianCalendar gc = parse(time);
		gc.set(what, count);
		return ToString(gc);
	}

	public static String add(int hourOfDay, int i, String time) {
		GregorianCalendar gc = parse(time);
		gc.add(hourOfDay, i);
		return ToString(gc);
	}
}
