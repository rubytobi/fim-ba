package Util;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class DateTime {
	private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");

	public static String timestamp() {
		return simpleDateFormat.format(now().getTime());
	}

	public static String ToString(GregorianCalendar calendar) {
		return simpleDateFormat.format(calendar.getTime());
	}

	public static GregorianCalendar now() {
		return new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles"));
	}

}
