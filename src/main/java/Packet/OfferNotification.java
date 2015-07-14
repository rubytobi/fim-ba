package Packet;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Paket, das an alle Devices verschikt wird, um ueber ein neues Angebot zu informieren
 *
 */
public class OfferNotification {
	private String location = null;
	private String referenceOffer = null;

	public OfferNotification() {
		// dummy konstruktor
	}
	
	/**
	 * Erstellt eine neue OfferNotification
	 * @param location			URL, unter der das neue Angebot zu finden ist
	 * @param referenceOffer	URL des vorhergehenden Angebots, auf dem das neue Angebot aufbaut 
	 */
	public OfferNotification(String location, String referenceOffer) {
		this.location = location;
		this.referenceOffer = referenceOffer;
	}

	/**
	 * Liefert die URL des neuen Angebots
	 * @return URL des neuen Angebots als String
	 */
	public String getLocation() {
		return location;
	}
	
	/**
	 * Liefert die URL des vorhergehenden Angebots
	 * @return URL des vorhergehenden Angebots als String
	 */
	public String getReferenceOffer() {
		return referenceOffer;
	}
	
	/**
	 * Liefert die Beschribung der OfferNotification
	 * @return String, welcher die URL des neuen und des vorhergehenden Angebot enthaelt
	 */
	public String toString() {
		return "[location=" + location + ",referenceOffer=" + referenceOffer + "]";
	}
	
	/**
	 * Gibt zurueck, ob das Angebot ein erstes Angebot ist
	 * @return false, wenn das Angebot ein vorhergehendes Angebot hat und somit kein erstes Angebot ist
	 * 			true, wenn das Angebot kein vorhergehendes Angebot hat und somit ein erstes Angebot ist
	 */
	@JsonIgnore
	public boolean isFirstOffer() {
		return referenceOffer == null;
	}
}
