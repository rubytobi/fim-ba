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
		/*
		 * Prüfe, dass bei einer Change Request keine Fehler geworfen werden
		 */

		/*
		 * Fridge fridge = new Fridge(8.0, 9.0, 4.0, 2.0, -0.5, 0.2, 1.0, 5.0);
		 * fridge.sendNewLoadprofile();
		 * 
		 * GregorianCalendar now = DateTime.now(); now.set(Calendar.MINUTE, 0);
		 * now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0);
		 * 
		 * double[] change = { 40, 9, 10, 10 };
		 * 
		 * ChangeRequestSchedule cr = new ChangeRequestSchedule(now, change);
		 * System.out.println("1. Change Request: ");
		 * fridge.changeLoadprofile(cr); System.out.println(
		 * "2. Change Request: "); fridge.changeLoadprofile(cr);
		 */

		/*
		 * Prüfe, dass ein neues Angebot auf den Marktplatz gestellt werden
		 * kann, wenn es für die aktuelle Stunde oder später ist
		 */
		double[] values = { 1.0, 2.0, 3.0, 4.0 };
		GregorianCalendar now = DateTime.now();
		now.set(Calendar.MINUTE, 0);
		now.set(Calendar.SECOND, 0);
		now.set(Calendar.MILLISECOND, 0);

		Loadprofile loadprofile = new Loadprofile(values, now);
		UUID uuid = UUID.randomUUID();
		Offer offer = new Offer(uuid, loadprofile);

		Marketplace mp = Marketplace.instance();
		mp.putOffer(offer);

		now.add(Calendar.HOUR_OF_DAY, +1);
		Loadprofile loadprofile2 = new Loadprofile(values, now);
		UUID uuid2 = UUID.randomUUID();
		Offer offer2 = new Offer(uuid2, loadprofile2);
		mp.putOffer(offer2);

		now.add(Calendar.HOUR_OF_DAY, +10);
		Loadprofile loadprofile3 = new Loadprofile(values, now);
		UUID uuid3 = UUID.randomUUID();
		Offer offer3 = new Offer(uuid3, loadprofile3);
		mp.putOffer(offer3);

	}
}
