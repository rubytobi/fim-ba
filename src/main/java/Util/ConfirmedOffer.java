package Util;

import java.util.GregorianCalendar;
import java.util.Calendar;

import Entity.Offer;

public class ConfirmedOffer implements Comparable<ConfirmedOffer> {
	private Offer offer;

	private int numSlots = 4;

	private GregorianCalendar realStartOffer;

	private GregorianCalendar timeConfirmed;

	private double priceConfirmed;

	private Type type;

	public enum Type {
		MATCHED, UNITPRICE, CHANGED
	};

	/**
	 * Erstellt ein neues ConfirmedOffer, wenn ein Angebot erstellt wurde.
	 * Dieses ConfirmedOffer enthält alle Informationen der Bestätigung eines
	 * Angebots.
	 * 
	 * @param offer
	 *            Angebot, welches bestätigt wurde.
	 * @param price
	 *            Preis, zu welchem das Angebot bestätigt wurde.
	 * @param type
	 *            Typ der Bestätigung (MATCHED, UNITPRICE, CHANGED)
	 */
	public ConfirmedOffer(Offer offer, double price, Type type) {
		this.offer = offer;
		this.priceConfirmed = price;
		this.type = type;

		timeConfirmed = DateTime.now();
		realStartOffer = chargeRealStartOffer();
	}

	/**
	 * Gibt zurück, ab welchem Zeitpunkt das Angebot wirklich startet, also ab
	 * welchem Slot die Werte des aggregierten Lastprofils != 0 sind
	 * 
	 * @return Startzeit des ersten Slots, in dem die Werte != 0 sind
	 */
	private GregorianCalendar chargeRealStartOffer() {
		GregorianCalendar start = (GregorianCalendar) offer.getDate().clone();

		// Prüfe, in welchem Slot die Werte zum Ersten Mal != 0 sind
		double[] values = offer.getAggLoadprofile().getValues();
		int slot = 3;
		for (int i = 0; i < numSlots; i++) {
			if (values[i] != 0) {
				slot = i;
				break;
			}
		}

		// Setze den Minutenwert von start auf den Beginn des ersten Slots mit
		// einem Wert != 0
		int minutes = slot * 15;
		start.set(Calendar.MINUTE, minutes);

		return start;
	}

	/**
	 * Ermöglicht es, alle bestätigten Angebote sortiert nach deren Typ
	 * auszugeben
	 */
	@Override
	public int compareTo(ConfirmedOffer offer) {
		Type otherType = offer.getType();

		if (type.equals(otherType)) {
			return 0;
		} else if (otherType.equals(Type.UNITPRICE) || type.equals(Type.MATCHED)) {
			return -1;
		} else {
			return 1;
		}
	}

	/**
	 * Gibt das Angebot zurück, welches bestätigt wurde.
	 * 
	 * @return Angebot, welches bestätigt wurde.
	 */
	public Offer getOffer() {
		return offer;
	}

	/**
	 * Gibt zurück, zu welchem Preis das Angebot bestätigt wurde.
	 * 
	 * @return double-Wert des Preises, zu dem das Angebot bestätigt wurde
	 */
	public double getPriceConfirmed() {
		return priceConfirmed;
	}

	/**
	 * Gibt den echten Startzeitpunkt des Angebots zurück. Dieser ist dann, wenn
	 * die Werte des aggregierten Lastprofils erstmals != 0 sind
	 * 
	 * @return Echter Startzeitpunkt
	 */
	public GregorianCalendar getRealStartOffer() {
		return realStartOffer;
	}

	/**
	 * Gibt zurück, zu welchem Zeitpunkt das Angebot bestätigt wurde.
	 * 
	 * @return	Zeitpunkt, zu dem das Angebot bestätigt wurde
	 */
	public GregorianCalendar getTimeConfirmed() {
		return timeConfirmed;
	}

	/**
	 * Gibt den Typ der Bestätigung des Angebots zurück. Der Typ kann hierbei
	 * nur MATCHED, UNITPRICE oder CHANGED sein.
	 * 
	 * @return Typ der Bestätigung des Angebots
	 */
	public Type getType() {
		return type;
	}

	/**
	 * Gibt den Typ der Bestätigung des Angebots als deutschen String zurück.
	 * 
	 * @return Deutsche Bezeichnung des Typs als String
	 */
	public String getTypeString() {
		if (type == Type.MATCHED) {
			return "Zusammengeführt";
		}
		if (type == Type.UNITPRICE) {
			return "Einheitspreis  ";
		}
		if (type == Type.CHANGED) {
			return "Anpassung      ";
		}
		return null;
	}
}
