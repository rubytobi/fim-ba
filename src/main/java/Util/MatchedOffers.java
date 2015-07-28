package Util;

import java.util.GregorianCalendar;

import java.util.UUID;

import Entity.Offer;

/**
 * Klasse fuer alle zusammengefuehrten Angebote
 *
 */
public class MatchedOffers {
	double price1, price2;
	GregorianCalendar time;
	Offer offer1, offer2;
	UUID uuid;
	
	public MatchedOffers (double price1, double price2, Offer offer1, Offer offer2) {
		this.price1 = price1;
		this.price2 = price2;
		this.offer1 = offer1;
		this.offer2 = offer2;
		uuid = UUID.randomUUID();
		time = offer1.getAggLoadprofile().getDate();
	}
	
	/**
	 * Gibt einen String der beiden Zusammengefuehrten Angebote zurueck.
	 * @return	String der beiden Zusammengefuehrten Angebote
	 */
	public String matchedOffersToString() {
		String o1 = " Offer 1: ";
		String o2 = " Offer 2: ";
		double[] values1 = offer1.getAggLoadprofile().getValues();
		double[] values2 = offer2.getAggLoadprofile().getValues();
		for (int i=0; i<values1.length; i++) {
			o1 = o1 + "[" +values1[i]+ "]";
			o2 = o2 + "[" +values2[i]+ "]";
		}
		String s = "UUID: " +uuid+ " time: " +DateTime.ToString(time);
		s = s + o1 + " Preis 1: " +price1;
		s = s + o2 + " Preis 2: " +price2;
		return s;
	}
	
	/**
	 * Liefert den Preis des 1. Angebots.
	 * @return Preis des 1. Angebots
	 */
	public double getPrice1() {
		return price1;
	}
	
	/**
	 * Liefert den Preis des 2. Angebots.
	 * @return Preis des 2. Angebots
	 */
	public double getPrice2() {
		return price2;
	}
	
	/**
	 * Liefert die Startzeit der beiden zusammengefuehrten Angebote.
	 * @return Startzeit der beiden zusammengefuehrten Angebote als GregorianCalendar
	 */
	public GregorianCalendar getTime() {
		return time;
	}
	
	/**
	 * Liefert die beiden zusammengefuehrten Angebote.
	 * @return Ein Array mit den beiden zusammengefuehrten Angeboten
	 */
	public Offer[] getOffers() {
		Offer[] offers = new Offer[2];
		offers[0] = offer1;
		offers[1] = offer2;
		return offers;
	}
	
	/**
	 * Liefert die UUID dieser Zusammenfuehrung von zwei Angeboten.
	 * @return	UUID der Zusammenfuehrung
	 */
	public UUID getUUID () {
		return uuid;
	}
}
