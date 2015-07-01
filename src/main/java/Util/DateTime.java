package Util;

import java.sql.Timestamp;
import java.util.Date;

public class DateTime {
	public static String timestamp() {
		Date date = new Date();
		return new Timestamp(date.getTime()).toString();
	}

}
