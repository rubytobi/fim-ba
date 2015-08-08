package start;

import java.util.Calendar;
import java.util.GregorianCalendar;
import Entity.Fridge;
import Packet.ChangeRequestSchedule;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		double before = 0;
		double after = 0;
		double maxTemp1 = 8;
		double maxTemp2 = 9;
		double minTemp1 = 4;
		double minTemp2 = 2;
		double spanTooHigh = maxTemp2 - maxTemp1;
		double spanTooLow = minTemp1 - minTemp2;
		double currentTempBefore = 3;
		double currentTempAfter = 2;
		if (currentTempBefore < minTemp1) {
			currentTempBefore -= minTemp2;
			before += 1 - currentTempBefore/spanTooLow;
		}
		else if (currentTempBefore > maxTemp1) {
			currentTempBefore -= maxTemp1;
			before += currentTempBefore/spanTooHigh;
		}
		if (currentTempAfter < minTemp1) {
			currentTempAfter -= minTemp2;
			after += 1 - currentTempAfter/spanTooLow;
		}
		else if (currentTempAfter > maxTemp1) {
			currentTempAfter -= maxTemp1;
			after += currentTempAfter/spanTooHigh;
		}
		System.out.println("Before: " +before);
		System.out.println("After: " +after);
		
		/*Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5, 2.0);
		fridge.sendNewLoadprofile();

		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		double[] change = { 40, 9, 10, 10 };

		ChangeRequestSchedule cr = new ChangeRequestSchedule(now, change);
		fridge.changeLoadprofile(cr);*/
	}
}
