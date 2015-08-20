package Packet;

import java.util.UUID;

/**
 * Klasse für Antworten des Marktplatzes auf Angebote. Je nachdem, an welche
 * Adresse dieses Paket gesendet wird, kann es eine bestätigung des Angebots
 * oder eine Änderungs- anfrage für das Angebot sein.
 *
 */
public class AnswerToOfferFromMarketplace {
	private double price;
	private UUID offer;


	public AnswerToOfferFromMarketplace() {
		// dummy
	}

	/**
	 * Erstellt neues Paket AnswerToOfferFromMarketplace
	 * 
	 * @param offer
	 *            UUID des Angebots, das bestaetigt wird
	 * @param price
	 *            Preis, zu dem das Angebot bestaetigt wird
	 */
	public AnswerToOfferFromMarketplace(UUID offer, double price) {
		this.price = price;
		this.offer = offer;
	}

	/**
	 * Liefert die UUID des Angebots
	 * 
	 * @return UUID des Angebots
	 */
	public UUID getOffer() {
		return offer;
	}

	/**
	 * Liefert den bestaetigten Preis
	 * 
	 * @return bestaetigten Preis
	 */
	public double getPrice() {
		return price;
	}
}
