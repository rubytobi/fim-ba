package Packet;

import java.util.UUID;

/**
 * Paket, das an alle Devices verschikt wird, um ueber ein neues Angebot zu
 * informieren
 *
 */
public class OfferNotification {
	private UUID consumer = null;
	private UUID offer = null;

	public OfferNotification() {
		// dummy konstruktor
	}

	/**
	 * Erstellt eine neue OfferNotification
	 * 
	 * @param location
	 *            URL, unter der das neue Angebot zu finden ist
	 * @param offerUUID
	 *            Angebots-ID
	 */
	public OfferNotification(UUID consumer, UUID offer) {
		this.consumer = consumer;
		this.offer = offer;
	}

	/**
	 * Liefert die URL des neuen Angebots
	 * 
	 * @return URL des neuen Angebots als String
	 */
	public UUID getConsumer() {
		return consumer;
	}

	public UUID getOffer() {
		return offer;
	}

	/**
	 * Liefert die Beschribung der OfferNotification
	 * 
	 * @return String, welcher die URL des neuen und des vorhergehenden Angebot
	 *         enthaelt
	 */
	public String toString() {
		return "OfferNotification [consumer=" + consumer + ",offer=" + offer + "]";
	}
}
