package Packet;

import java.util.UUID;

/**
 * Paket, das an alle Devices verschikt wird, um ueber ein neues Angebot zu
 * informieren
 *
 */
public class OfferNotification {
	private String location = null;
	private UUID offerUUID = null;

	public OfferNotification() {
		// dummy konstruktor
	}

	/**
	 * Erstellt eine neue OfferNotification
	 * 
	 * @param location
	 *            URL, unter der das neue Angebot zu finden ist
	 * @param referenceOffer
	 *            URL des vorhergehenden Angebots, auf dem das neue Angebot
	 *            aufbaut
	 */
	public OfferNotification(String location, UUID offerUUID) {
		this.location = location;
		this.offerUUID = offerUUID;
	}

	/**
	 * Liefert die URL des neuen Angebots
	 * 
	 * @return URL des neuen Angebots als String
	 */
	public String getLocation() {
		return location;
	}

	public UUID getOfferUUID() {
		return offerUUID;
	}

	/**
	 * Liefert die Beschribung der OfferNotification
	 * 
	 * @return String, welcher die URL des neuen und des vorhergehenden Angebot
	 *         enthaelt
	 */
	public String toString() {
		return "[location=" + location + ",referenceOffer=" + offerUUID.toString() + "]";
	}
}
