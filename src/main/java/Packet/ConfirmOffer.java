package Packet;

import java.util.UUID;

public class ConfirmOffer {
	private double price;
	private UUID offer;
	
	public ConfirmOffer (UUID offer, double price) {
		this.price = price;
		this.offer = offer;
	}
	
	public UUID getOffer() {
		return offer;
	}
	
	public double getPrice() {
		return price;
	}
}
