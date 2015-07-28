package Util;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.Calendar;

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

	public static GregorianCalendar parse(String calendar) {
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
	 * @param date
	 * @return
	 */
	public static GregorianCalendar stringToCalendar(String date) {
		Date time = new Date();
		try {
			time = simpleDateFormat.parse(date);
		}
		catch (Exception e) {
		}
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTime(time);
		return calendar;
	}

}
