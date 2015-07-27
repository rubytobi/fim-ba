package Util;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DateTime {
	private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

	public static String timestamp() {
		return simpleDateFormat.format(now().getTime());
	}

	public static String ToString(GregorianCalendar calendar) {
		return simpleDateFormat.format(calendar.getTime());
	}

	public static GregorianCalendar now() {
		return new GregorianCalendar(TimeZone.getTimeZone("America/Los Angeles"));
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

}
