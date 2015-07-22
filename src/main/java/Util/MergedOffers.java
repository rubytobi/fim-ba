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
	
	public String mergedOffersToString() {
		String o1 = " Offer 1: ";
		String o2 = " Offer 2: ";
		double[] values1 = offer1.getAggLoadprofile().getValues();
		double[] values2 = offer2.getAggLoadprofile().getValues();
		for (int i=0; i<values1.length; i++) {
			o1 = o1 + "[" +values1[i]+ "]";
			o2 = o2 + "[" +values2[i]+ "]";
		}
		String s = "UUID: " +uuid+ " time: " +DateTime.ToString(time);
		s = s + o1 + " Preis 1: " +price1;
		s = s + o2 + " Preis 2: " +price2;
		return s;
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
