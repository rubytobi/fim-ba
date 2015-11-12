package Util;

import Entity.Offer;


public class ConfirmedOffer implements Comparable<ConfirmedOffer> {
	private Offer offer;
	
	private double priceConfirmed;
	
	private Type type;
	
	public enum Type {
		MATCHED, UNITPRICE, CHANGED
	};
	
	public ConfirmedOffer(Offer offer, double price, Type type) {
		this.offer = offer;
		this.priceConfirmed = price;
		this.type = type;
	}
	
	public Offer getOffer() {
		return offer;
	}
	
	public double getPriceConfirmed() {
		return priceConfirmed;
	}
	
	public Type getType() {
		return type;
	}

	public String getTypeString() {
		if (type == Type.MATCHED) {
			return "M";
		}
		if (type == Type.UNITPRICE) {
			return "U";
		}
		if (type == Type.CHANGED) {
			return "C";
		}
		return null;		
	}
	
	@Override
	public int compareTo(ConfirmedOffer offer) {
		Type otherType = offer.getType();
		
		if (type.equals(otherType)) {
			return 0;
		} else if (otherType.equals(Type.UNITPRICE) || type.equals(Type.MATCHED)) {
			return -1;
		} else {
			return 1;
		}
	}
}
