package start;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import Entity.Fridge;
import Entity.Loadprofile;
import Entity.Offer;
import Entity.Marketplace;
import Entity.BHKW;

import Packet.ChangeRequestSchedule;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		BHKW bhkw = new BHKW(1, 5, 1, 100, 5);
		
		bhkw.sendNewLoadprofile();
		
		double[] values = {0, 1, 0, 1};
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE,  0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);
		now.add(Calendar.HOUR_OF_DAY,  1);
		ChangeRequestSchedule cr = new ChangeRequestSchedule(now, values);
		bhkw.receiveChangeRequestSchedule(cr);
	}
}
