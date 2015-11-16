package Util;

import java.util.GregorianCalendar;

import java.util.UUID;

import Entity.Offer;

/**
 * Klasse fuer alle zusammengefuehrten Angebote
 *
 */
public class MatchedOffers {
	/**
	 * Die Gesamtpreise, zu welchen die beiden Angebote zusammengeführt wurden.
	 */
	private double allRoundPrice1, allRoundPrice2;

	/**
	 * Abweichung von der Prognose vor und nach der Zusamenführung
	 */
	private double deviationBefore, deviationAfter;

	/**
	 * Die Anzahl an Slots eines Lastprofils.
	 */
	private int numSlots = 4;

	/**
	 * Die beiden zusammengeführten Angebote
	 */
	private Offer offer1, offer2;

	/**
	 * Die Preise, zu welchen die beiden Angebote zusammengeführt wurden.
	 */
	private double price1, price2;

	/**
	 * Der Startzeitpunkt der beiden Angebote
	 */
	private GregorianCalendar time;

	/**
	 * UUID der Zusammenführung
	 */
	private UUID uuid;

	/**
	 * Speichert alle Informationen zu zwei zusammengeführten Angeboten.
	 * 
	 * @param price1
	 *            Preis der Zusammenführung des 1. Angebots
	 * @param price2
	 *            Preis der Zusammenführung des 2. Angebots
	 * @param offer1
	 *            1. Angebot
	 * @param offer2
	 *            2. Angebot
	 * @param deviationBefore
	 *            Abweichung von der Prognose vor der Zusammenführung
	 * @param deviationAfter
	 *            Abweichung von der Prognose nach der Zusammenführung
	 */
	public MatchedOffers(double price1, double price2, Offer offer1, Offer offer2, double deviationBefore,
			double deviationAfter) {
		this.price1 = price1;
		this.price2 = price2;
		this.offer1 = offer1;
		this.offer2 = offer2;
		this.deviationBefore = deviationBefore;
		this.deviationAfter = deviationAfter;

		uuid = UUID.randomUUID();
		time = offer1.getAggLoadprofile().getDate();

		chargeAllRoundPrices();
	}

	/**
	 * Berechnet die Gesamtpreise der beiden Angebote
	 */
	private void chargeAllRoundPrices() {
		// Berechne zunächst die jeweilige Summer der aggregierten Lastprofile
		double[] values1 = offer1.getAggLoadprofile().getValues();
		double[] values2 = offer2.getAggLoadprofile().getValues();
		double sum1 = 0;
		double sum2 = 0;
		for (int i = 0; i < numSlots; i++) {
			sum1 += values1[i];
			sum2 += values2[i];
		}

		// Berechne nun die Gesamtpreise
		allRoundPrice1 = sum1 * price1;
		allRoundPrice2 = sum2 * price2;
	}

	/**
	 * Gibt den Gesamtpreis des 1. Angebots zurück
	 * 
	 * @return double-Wert des Gesamtpreises des ersten Angebots
	 */
	public double getAllRoundPrice1() {
		return allRoundPrice1;
	}

	/**
	 * Gibt den Gesamtpreis des 2. Angebots zurück
	 * 
	 * @return double-Wert des Gesamtpreises des zweiten Angebots
	 */
	public double getAllRoundPrice2() {
		return allRoundPrice2;
	}

	/**
	 * Gibt die Abweichung von der Prognose nach der Zusammenführung zurück.
	 * 
	 * @return double-Wert der Abweichung von der Prognose nach der
	 *         Zusammenführung
	 */
	public double getDeviationAfter() {
		return deviationAfter;
	}

	/**
	 * Gibt die Abweichung von der Prognose vor der Zusammenführung zurück.
	 * 
	 * @return double-Wert der Abweichung von der Prognose vor der
	 *         Zusammenführung
	 */
	public double getDeviationBefore() {
		return deviationBefore;
	}

	/**
	 * Liefert die beiden zusammengefuehrten Angebote.
	 * 
	 * @return Ein Array mit den beiden zusammengefuehrten Angeboten
	 */
	public Offer[] getOffers() {
		Offer[] offers = new Offer[2];
		offers[0] = offer1;
		offers[1] = offer2;
		return offers;
	}

	/**
	 * Liefert den Preis des 1. Angebots.
	 * 
	 * @return Preis des 1. Angebots
	 */
	public double getPrice1() {
		return price1;
	}

	/**
	 * Liefert den Preis des 2. Angebots.
	 * 
	 * @return Preis des 2. Angebots
	 */
	public double getPrice2() {
		return price2;
	}

	/**
	 * Liefert die Startzeit der beiden zusammengefuehrten Angebote.
	 * 
	 * @return Startzeit der beiden zusammengefuehrten Angebote als
	 *         GregorianCalendar
	 */
	public GregorianCalendar getTime() {
		return time;
	}

	/**
	 * Liefert die UUID dieser Zusammenfuehrung von zwei Angeboten.
	 * 
	 * @return UUID der Zusammenfuehrung
	 */
	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Gibt einen String der beiden Zusammengefuehrten Angebote zurueck.
	 * 
	 * @return String der beiden Zusammengefuehrten Angebote
	 */
	public String matchedOffersToString() {
		String o1 = " Offer 1: ";
		String o2 = " Offer 2: ";
		double[] values1 = offer1.getAggLoadprofile().getValues();
		double[] values2 = offer2.getAggLoadprofile().getValues();
		for (int i = 0; i < values1.length; i++) {
			o1 = o1 + "[" + values1[i] + "]";
			o2 = o2 + "[" + values2[i] + "]";
		}
		String s = "UUID: " + uuid + " time: " + DateTime.ToString(time);
		s = s + o1 + " Preis 1: " + price1;
		s = s + "\n" + o2 + " Preis 2: " + price2;
		return s;
	}
}
