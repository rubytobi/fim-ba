package Util;

import java.sql.Timestamp;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DateTime {
	public static String timestamp() {
		Date date = new Date();
		return new Timestamp(date.getTime()).toString();
	}

	public static String ToString(GregorianCalendar calendar) {
		return new Timestamp(calendar.getTime().getTime()).toString();
	}

	public static GregorianCalendar now() {
		return new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles"));
	}

}
