package Util;

import java.util.GregorianCalendar;

import Entity.Offer;
import start.Loadprofile;

public class PossibleMerge {
	private Offer offer1, offer2;
	
	private double[] valuesAggLoadprofile;
	
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
	
	public GregorianCalendar getDate() {
		return offer1.getDate();
	}
}
