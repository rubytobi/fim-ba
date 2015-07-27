package Util;

import java.util.Comparator;

import Entity.Offer;

public class sortOfferPriceDemandHighToLow implements Comparator<Offer>{
	@Override
	public int compare(Offer offer1, Offer offer2) {
		double price1 = offer1.getPrice();
		double price2 = offer2.getPrice();
		if (price1<price2) {
			return 1;
		}
		else if (price1 == price2) {
			return 0;
		}
		else {
			return -1;
		}
	}
}
