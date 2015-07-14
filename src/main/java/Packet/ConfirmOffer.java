package Packet;

import java.util.UUID;

/**
 * Paket, das zum Bestaetigen eines Angebots versendet wird
 */
public class ConfirmOffer {
	private double price;
	private UUID offer;
	
	/**
	 * Erstellt neues ConfirmOffer
	 * @param offer UUID des Angebots, das bestaetigt wird
	 * @param price	Preis, zu dem das Angebot bestaetigt wird
	 */
	public ConfirmOffer (UUID offer, double price) {
		this.price = price;
		this.offer = offer;
	}
	
	/**
	 * Liefert die UUID des Angebots
	 * @return UUID des Angebots
	 */
	public UUID getUuid() {
		return offer;
	}
	
	/**
	 * Liefert den bestaetigten Preis
	 * @return bestaetigten Preis
	 */
	public double getPrice() {
		return price;
	}
}
