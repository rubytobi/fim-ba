package Util;

import java.util.GregorianCalendar;

import Entity.Offer;
import start.Loadprofile;

public class PossibleMerge implements Comparable<PossibleMerge>{
	private Offer offer1, offer2;
	
	private double[] valuesAggLoadprofile;
	
	private double numSlots = 4;
	
	public PossibleMerge(Offer offer1, Offer offer2) {
		if (! offer1.getDate().equals(offer2.getDate())) {
			// TODO Fehler, dass Angebote für unterschiedlichen Zeitraum sind
			return;
		}
		this.offer1 = offer1;
		this.offer2 = offer2;
		Loadprofile loadprofile = new Loadprofile(offer1.getAggLoadprofile(), offer2.getAggLoadprofile());
		this.valuesAggLoadprofile = loadprofile.getValues();
	}
	
	public Offer[] getOffers() {
		Offer[] offers = {offer1, offer2};
		return offers;
	}
	
	public double[] getValuesAggLoadprofile() {
		return valuesAggLoadprofile;
	}
	
	public double getSumAggLoadprofile() {
		double sumAggLoadprofile = 0;
		for (int i=0; i<numSlots; i++) {
			sumAggLoadprofile += valuesAggLoadprofile[i];
		}
		return sumAggLoadprofile;
	}
	
	public GregorianCalendar getDate() {
		return offer1.getDate();
	}
	
	public String toString() {
		return "sumAggLoadprofile: " +getSumAggLoadprofile()+ " Price Offers: " +offer1.getPrice()+ ", " +offer2.getPrice();
	}
	
	@Override
	public int compareTo(PossibleMerge possibleMerge) {
		double thisSum = Math.abs(this.getSumAggLoadprofile());
		double otherSum = Math.abs(possibleMerge.getSumAggLoadprofile());
		if (thisSum < otherSum) {
			return -1;
		}
		else if (thisSum == otherSum) {
			return 0;
		}
		else {
			return 1;
		}
	}
}
