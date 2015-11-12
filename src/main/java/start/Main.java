package start;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.ArrayList;

import Entity.Fridge;
import Entity.Loadprofile;
import Entity.Offer;
import Entity.Loadprofile.Type;
import Entity.Marketplace;
import Entity.BHKW;

import Packet.ChangeRequestSchedule;
import Util.DateTime;
import Util.ConfirmedOffer;

public class Main {
	public static void main(String[] args) {	
		GregorianCalendar now = DateTime.now();
		
		double[] values = {1, 2, 3, 4};
		Loadprofile lp = new Loadprofile(values, now, 1, 0, 2, Type.MIXED);
		
		UUID uuid1 = UUID.randomUUID();
		Offer o1 = new Offer(uuid1, lp);
		
		ConfirmedOffer c1 = new ConfirmedOffer(o1, 1, ConfirmedOffer.Type.CHANGED);
		ConfirmedOffer c2 = new ConfirmedOffer(o1, 2, ConfirmedOffer.Type.UNITPRICE);
		ConfirmedOffer c3 = new ConfirmedOffer(o1, 3, ConfirmedOffer.Type.UNITPRICE);
		ConfirmedOffer c4 = new ConfirmedOffer(o1, 4, ConfirmedOffer.Type.MATCHED);
		ConfirmedOffer c5 = new ConfirmedOffer(o1, 5, ConfirmedOffer.Type.MATCHED);
		ConfirmedOffer c6 = new ConfirmedOffer(o1, 6, ConfirmedOffer.Type.CHANGED);
		ConfirmedOffer c7 = new ConfirmedOffer(o1, 7, ConfirmedOffer.Type.UNITPRICE);
		ConfirmedOffer c8 = new ConfirmedOffer(o1, 8, ConfirmedOffer.Type.CHANGED);
		
		ArrayList<ConfirmedOffer> all = new ArrayList<ConfirmedOffer>();
		all.add(c1);
		all.add(c2);
		all.add(c3);
		all.add(c4);
		all.add(c5);
		all.add(c6);
		all.add(c7);
		all.add(c8);
		
		Collections.sort(all);
		
		confirmedOffersToString(all, now);

		
	}
	
	public static void confirmedOffersToString(ArrayList<ConfirmedOffer> list, GregorianCalendar time) {
		System.out.println("Confirmed Offers at " + DateTime.ToString(time) +  ":");
		double countMatched = 0, countChanged = 0, countUnitPrice = 0;
		for (ConfirmedOffer offer: list) {
			// Zähle die jeweilige Variable hoch
			if (offer.getType().equals(ConfirmedOffer.Type.MATCHED)) {
				countMatched++;
			}
			if (offer.getType().equals(ConfirmedOffer.Type.CHANGED)) {
				countChanged++;
			}
			if (offer.getType().equals(ConfirmedOffer.Type.UNITPRICE)) {
				countUnitPrice++;
			}
			
			// Berechne den Gesamtpreis des Angebots
			double totalPrice = offer.getOffer().getSumAggLoadprofile()*offer.getPriceConfirmed();
			
					
			// Gebe das aktuelle Angebot aus
			String s = offer.getTypeString() + ", Total Price: " + totalPrice;
			double[] values = offer.getOffer().getAggLoadprofile().getValues();
			s = s + ", Values: ";
			for (int i=0; i<values.length; i++) {
				s = s + "[" +values[i] + "]";
			}
			s = s + ", Price per kWh: " +offer.getPriceConfirmed();
			System.out.println(s);
		}
		double sum = countMatched + countChanged + countUnitPrice;
		double percentageMatched = Math.round(1000.00 * (countMatched/sum)) / 10.0;
		double percentageChanged = Math.round(1000.00 * (countChanged/sum)) / 10.0;
		double percentageUnitPrice =  Math.round(1000.00 * (countUnitPrice/sum)) / 10.0;
		System.out.println("\nVon " +sum+ " bestätigten Angeboten sind " +countMatched+ ", also " +percentageMatched+ "% gematcht.");
		System.out.println("Von " +sum+ " bestätigten Angeboten sind " +countChanged+ ", also " +percentageChanged+ "% geändert.");
		System.out.println("Von " +sum+ " bestätigten Angeboten sind " +countUnitPrice+ ", also " +percentageUnitPrice+ "% zum Einheitspreis bestätigt.");
		System.out.println("Es wurden also " +percentageMatched+ "% der Angebote ohne Eingreifen des BKV zusammengeführt.");
	}
}
