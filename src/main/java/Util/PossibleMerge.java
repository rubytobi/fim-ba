package Util;

import java.util.GregorianCalendar;

import Entity.Offer;
import start.Loadprofile;

public class PossibleMerge implements Comparable<PossibleMerge>{
	private Offer offer1, offer2;
	
	private double[] valuesAggLoadprofile;
	
	private double numSlots = 4;
	
	private double outcomeMerge;
	
	public PossibleMerge(Offer offer1, Offer offer2) {
		if (! offer1.getDate().equals(offer2.getDate())) {
			// TODO Fehler, dass Angebote für unterschiedlichen Zeitraum sind
			return;
		}
		this.offer1 = offer1;
		this.offer2 = offer2;
		Loadprofile loadprofile = new Loadprofile(offer1.getAggLoadprofile(), offer2.getAggLoadprofile());
		this.valuesAggLoadprofile = loadprofile.getValues();
		this.outcomeMerge = chargeOutcomeMerge(offer1, offer2);
	}
	
	private double chargeOutcomeMerge(Offer offer1, Offer offer2) {
		double price1 = offer1.getPrice();
		double price2 = offer2.getPrice();
		double[] loadprofile1 = offer1.getAggLoadprofile().getValues();
		double sumOffer1 = 0;
		double[] loadprofile2 = offer2.getAggLoadprofile().getValues();
		double sumOffer2 = 0;
		for (int i=0; i<numSlots; i++) {
			sumOffer1 += loadprofile1[i];
			sumOffer2 += loadprofile2[i];
		}
		return sumOffer1*price1 + sumOffer2*price2;
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
		return "outcomeMerge: " +outcomeMerge+ " Price Offers: " +offer1.getPrice()+ ", " +offer2.getPrice();
	}
	
	public double getOutcomeMerge() {
		return outcomeMerge;
	}
	
	@Override
	public int compareTo(PossibleMerge possibleMerge) {
		double otherOutcome = possibleMerge.getOutcomeMerge();
		if (outcomeMerge < otherOutcome) {
			return -1;
		}
		else if (outcomeMerge == otherOutcome) {
			return 0;
		}
		else {
			return 1;
		}
	}
}
