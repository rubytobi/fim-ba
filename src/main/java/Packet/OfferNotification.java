package Packet;

import java.util.UUID;

public class OfferNotification {
	private String location = null;
	private UUID offerUUID = null;

	public OfferNotification() {
		// dummy konstruktor
	}

	/**
	 * Benachrichtigung zwischen Consumern über neue Angebote
	 * 
	 * @param location
	 *            Ort des neuen Angebots
	 * @param offerUUID
	 *            explizite Angebots-ID (in location auch enthalten)
	 */
	public OfferNotification(String location, UUID offerUUID) {
		this.location = location;
		this.offerUUID = offerUUID;
	}

	public String getLocation() {
		return location;
	}

	public UUID getOfferUUID() {
		return offerUUID;
	}

	public String toString() {
		return "[location=" + location + ",referenceOffer=" + offerUUID.toString() + "]";
	}
}
