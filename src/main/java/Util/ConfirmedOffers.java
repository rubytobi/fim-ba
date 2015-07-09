package Util;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import java.util.UUID;

import Entity.Offer;

public class ConfirmedOffers {
	double price;
	GregorianCalendar time;
	Offer offer1, offer2;
	UUID uuid;
	
	public ConfirmedOffers (double price, Offer offer1, Offer offer2) {
		this.price = price;
		this.offer1 = offer1;
		this.offer2 = offer2;
		uuid = UUID.randomUUID();
		time = offer1.getAggLoadprofile().getDate();
	}
	
	public double getPrice() {
		return price;
	}
	
	public GregorianCalendar getTime() {
		return time;
	}
	
	public ArrayList<Offer> getOffers() {
		ArrayList<Offer> offers = new ArrayList<Offer>();
		offers.add(offer1);
		offers.add(offer2);
		return offers;
	}
	
	public UUID getUUID () {
		return uuid;
	}
}
