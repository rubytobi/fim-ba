package start;

import java.util.Calendar;
import java.util.GregorianCalendar;
import Entity.Fridge;
import Packet.ChangeRequestSchedule;
import Util.DateTime;

public class Main {
	public static void main(String[] args) {
		double newPriceSugg;
		double sumAggLoadprofileWithPrivileges = 5;
		double priceSuggWithPrivileges = 0;
		double sumAggLoadprofileWithoutPrivileges = 10;
		double priceSuggWithoutPrivileges = 9;

		double weight = sumAggLoadprofileWithPrivileges / sumAggLoadprofileWithoutPrivileges;

		double weightWithPrivileges = 1 / (weight + 1) * weight;
		double weightWithoutPrivileges = 1 - weightWithPrivileges;

		newPriceSugg = Math.round(100.00 * (weightWithPrivileges * priceSuggWithPrivileges
				+ weightWithoutPrivileges * priceSuggWithoutPrivileges)) / 100.00;

		/*
		 * Fridge fridge = new Fridge(8, 9, 4, 2, -0.5, 0.2, 1, 5, 2.0);
		 * fridge.sendNewLoadprofile();
		 * 
		 * GregorianCalendar now = DateTime.now(); now.set(Calendar.MINUTE, 0);
		 * now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0);
		 * 
		 * double[] change = { 40, 9, 10, 10 };
		 * 
		 * ChangeRequestSchedule cr = new ChangeRequestSchedule(now, change);
		 * fridge.changeLoadprofile(cr);
		 */
	}
}
