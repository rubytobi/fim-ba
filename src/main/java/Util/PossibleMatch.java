package Util;

import java.util.GregorianCalendar;

import Entity.Loadprofile;
import Entity.Offer;

public class PossibleMatch implements Comparable<PossibleMatch>{
	private Offer offer1, offer2;
	
	private double[] valuesAggLoadprofile;
	
	private int numSlots = 4;
	
	private double outcomeMatch;
	
	public PossibleMatch(Offer offer1, Offer offer2) {
		if (! offer1.getDate().equals(offer2.getDate())) {
			// TODO Fehler, dass Angebote fuer unterschiedlichen Zeitraum sind
			return;
		}
		this.offer1 = offer1;
		this.offer2 = offer2;
		Loadprofile loadprofile = new Loadprofile(offer1.getAggLoadprofile(), offer2.getAggLoadprofile());
		this.valuesAggLoadprofile = loadprofile.getValues();
		this.outcomeMatch = chargeOutcomeMatch();
	}
	
	/**
	 * Berechnet den finanziellen Output des moeglichen Matches.
	 * @return Finanzieller Output.
	 */
	private double chargeOutcomeMatch() {
		double price1 = offer1.getPriceSugg();
		double price2 = offer2.getPriceSugg();
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
	
	/**
	 * Liefert die beiden Angebote des moeglichen Matches.
	 * @return Ein Array mit den beiden zusammengefuehrten Angeboten
	 */
	public Offer[] getOffers() {
		Offer[] offers = {offer1, offer2};
		return offers;
	}
	
	/**
	 * Liefert das aggregierte Lastprofil der beiden Angebote
	 * des moeglichen Matches.
	 * @return Array mit den Werten des aggregierten Lastprofils
	 */
	public double[] getValuesAggLoadprofile() {
		return valuesAggLoadprofile;
	}
	
	/**
	 * Liefert die Summe des aggregierten Lastprofils der 
	 * beiden Angebote des moeglichen Matches.
	 * @return	Summe des aggregierten Lastprofils
	 */
	public double getSumAggLoadprofile() {
		double sumAggLoadprofile = 0;
		for (int i=0; i<numSlots; i++) {
			sumAggLoadprofile += valuesAggLoadprofile[i];
		}
		return sumAggLoadprofile;
	}
	
	/**
	 * Liefert das Datum der beiden Angebote des moeglichen
	 * Matches.
	 * @return Das Datum der beiden Angebote als GregorianCalendar
	 */
	public GregorianCalendar getDate() {
		return offer1.getDate();
	}
	
	/**
	 * Liefert alle Informationen des moeglichen Matches
	 * als String.
	 * @return String mit allen Informationen des moeglichen Matches
	 */
	public String toString() {
		String agg = " Agg: ";
		String o1 = "Offer1: ";
		String o2 = " Offer2: ";
		String outcome = " Outcome: " +outcomeMatch;
		double[] values1 = offer1.getAggLoadprofile().getValues();
		double[] values2 = offer2.getAggLoadprofile().getValues();
		for (int i=0; i<numSlots; i++) {
			agg = agg + "["+ valuesAggLoadprofile[i] +"]";
			o1 = o1 +"["+ values1[i]+"]";
			o2 = o2 +"["+ values2[i]+"]";
		}
		return o1 + o2 + agg +outcome;
	}
	
	/**
	 * Berechnet den finanziellen Output des moeglichen
	 * Matches.
	 * @return	Finanziellen Output des moeglichen Matches
	 */
	public double getOutcomeMatch() {
		return outcomeMatch;
	}
	
	/**
	 * Ueberschreibt die Methode compareTo, so dass die moeglichen
	 * Matches aufsteigen nach ihrem finanziellen Output 
	 * sortiert werden koennen.
	 */
	@Override
	public int compareTo(PossibleMatch possibleMatch) {
		double otherOutcome = possibleMatch.getOutcomeMatch();
		if (outcomeMatch < otherOutcome) {
			return -1;
		}
		else if (outcomeMatch == otherOutcome) {
			return 0;
		}
		else {
			return 1;
		}
	}
	
	/**
	 * Ueberschreibt die Method equals.
	 * Zwei moegliche Matches werden als gleich angesehen, 
	 * wenn sie aus den gleichen Angeboten bestehen.
	 */
	@Override
	public boolean equals (Object o) {
		PossibleMatch possibleMatchToCompare = (PossibleMatch) o;
		Offer[] offersToCompare = possibleMatchToCompare.getOffers();
		if (offersToCompare[0].equals(offer1) && offersToCompare[1].equals(offer2) 
				|| offersToCompare[1].equals(offer1) && offersToCompare[0].equals(offer2)) {
			return true;
		}
		else {
			return false;
		}
	}
}
