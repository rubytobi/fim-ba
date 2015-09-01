package Util;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Calendar;

public class DateTime {
	private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	private static GregorianCalendar programStart = null;

	private static double factorTimeScaling = 1;

	public static String timestamp() {
		return simpleDateFormat.format(now().getTime());
	}

	public static String ToString(GregorianCalendar calendar) {
		return simpleDateFormat.format(calendar.getTime());
	}

	public static GregorianCalendar now() {
		GregorianCalendar realTime = new GregorianCalendar(TimeZone.getTimeZone("America/Los Angeles"));

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

		millisToAdd *= factorTimeScaling - 1;
		simulationTime.add(Calendar.MILLISECOND, millisToAdd);

		return simulationTime;
	}

	public static GregorianCalendar parse(String calendar) throws IllegalArgumentException {
		GregorianCalendar gc = new GregorianCalendar();
		Date date = null;
		try {
			date = simpleDateFormat.parse(calendar);
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

	public static GregorianCalendar currentTimeSlot() {
		GregorianCalendar now = now();

		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		return now;
	}

	public static GregorianCalendar nextTimeSlot() {
		GregorianCalendar now = now();

		now.set(Calendar.HOUR, now.get(Calendar.HOUR) + 1);
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		return now;
	}

}
