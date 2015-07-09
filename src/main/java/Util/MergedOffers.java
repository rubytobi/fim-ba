package Util;

import java.util.ArrayList;
import java.util.GregorianCalendar;

import java.util.UUID;

import Entity.Offer;

public class MergedOffers {
	double price1, price2;
	GregorianCalendar time;
	Offer offer1, offer2;
	UUID uuid;
	
	public MergedOffers (double price1, double price2, Offer offer1, Offer offer2) {
		this.price1 = price1;
		this.price2 = price2;
		this.offer1 = offer1;
		this.offer2 = offer2;
		uuid = UUID.randomUUID();
		time = offer1.getAggLoadprofile().getDate();
	}
	
	public double getPrice1() {
		return price1;
	}
	
	public double getPrice2() {
		return price2;
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
