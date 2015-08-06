package start;

import java.util.Calendar;
import java.util.GregorianCalendar;
import Entity.Fridge;
import Packet.ChangeRequestSchedule;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5, 2.0);
		fridge.sendNewLoadprofile();

		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		double[] change = { 40, 9, 10, 10 };

		ChangeRequestSchedule cr = new ChangeRequestSchedule(now, change);
		fridge.changeLoadprofile(cr);
	}
}
