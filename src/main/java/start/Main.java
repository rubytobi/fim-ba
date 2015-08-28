package start;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import Entity.Fridge;
import Entity.Loadprofile;
import Entity.Offer;
import Entity.Marketplace;

import Packet.ChangeRequestSchedule;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		double[] values = { 1.0, 2.0, 3.0, 4.0 };
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		Loadprofile loadprofile = new Loadprofile(values, now, 2.0, 5.0, 8.0, Loadprofile.Type.INITIAL);
		System.out.println("Max: " + loadprofile.getMaxPrice());
		System.out.println("Min: " + loadprofile.getMinPrice());

		double startTemp = 5.5;
		double maxPrice = Math.max(0, 5);
		double minPrice = Math.min(0, 5);
		double minTemp1 = 4.0;
		double maxTemp1 = 6.5;
		if (startTemp <= minTemp1) {
			System.out.println(minPrice);
		} else if (startTemp >= maxTemp1) {
			System.out.println(maxPrice);
		} else {
			double span = maxTemp1 - minTemp1;
			startTemp = startTemp - minTemp1;
			System.out.println((Math.round(100.00 * (startTemp / span)) / 100.00) * maxPrice + minPrice);
		}

	}
}
