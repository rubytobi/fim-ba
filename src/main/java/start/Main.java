package start;

import java.util.Calendar;
import java.util.GregorianCalendar;

import Entity.Fridge;
import Packet.ChangeRequest;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5);
		fridge.sendNewLoadprofile();
		GregorianCalendar time = DateTime.now();
		time.set(Calendar.MINUTE, 0);
		time.set(Calendar.SECOND, 0);
		time.set(Calendar.MILLISECOND, 0);
		time.add(Calendar.HOUR_OF_DAY, 1);
		double[] changes = {1.0, -2.0, 0.0, 0.0};
		ChangeRequest cr = new ChangeRequest(time, changes);
		fridge.changeLoadprofile(cr);
	}
}
