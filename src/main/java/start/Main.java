package start;

import Entity.Offer;

public class Main {
	public static void main(String[] args) {
		double[] valuesOffer1 = {0.0, 3.0, 5.0, 2.0};
		double[] valuesOffer2 = {0.0, -2.5, -6.0, 2.0};
		double priceOffer1 = 0.5;
		double priceOffer2 = 0.8;
		double[] deviation = {0.0, 0.5, -0.5, 4.0};
		
		mergeOffers(valuesOffer2, valuesOffer1, priceOffer2, priceOffer1, deviation);
	}
	
	private static void mergeOffers (double[] valuesOffer1, double[] valuesOffer2, 
			double priceOffer1, double priceOffer2, double[] deviation) {		
		
		double eexPrice = 0.5;
		int numSlots = 4;
		
		double sumOffer1 = 0;
		double sumOffer2 = 0;
		double sumOfferDemand, sumOfferSupply, priceOfferDemand, priceOfferSupply;
		double[] valuesOfferDemand, valuesOfferSupply;
		for (int i=0; i<4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}
		if (sumOffer1<0 && sumOffer2>0) {
			sumOfferDemand = sumOffer1;
			valuesOfferDemand = valuesOffer1;
			priceOfferDemand = priceOffer1;
			
			sumOfferSupply = sumOffer2;
			valuesOfferSupply = valuesOffer2;
			priceOfferSupply = priceOffer2;
		}
		else if (sumOffer1 > 0 && sumOffer2 < 0) {
			sumOfferDemand = sumOffer2;
			valuesOfferDemand = valuesOffer2;
			priceOfferDemand = priceOffer2;
			
			sumOfferSupply = sumOffer1;
			valuesOfferSupply = valuesOffer1;
			priceOfferSupply = priceOffer1;
		}
		else {
			// TODO Throw exception
			return;
		}
		
		double priceDemand, priceSupply;
		
		// Lege Preis für beide Angebote ohne "Strafe" fest
		if (sumOfferDemand + sumOfferSupply == 0) {
			priceDemand = (priceOfferDemand+priceOfferSupply)/2;
			priceSupply = priceDemand;
		}
		else {
			// Ermittle Gesamtpreis für beide Angebote
			priceDemand = sumOfferDemand*priceOfferDemand;
			priceSupply = sumOfferSupply*priceOfferSupply;
			// Ermittle Mittelwert von Betrag des Gesamtpreises beider Angebote
			double price = (Math.abs(priceDemand)+Math.abs(priceSupply))/2;
			// Weise den Gesamtpreisen den jeweiligen Mittelwert zu
			priceDemand = -price;
			priceSupply = price;
			// Berechne Preise pro kWh für Angebote
			priceDemand = priceDemand/sumOfferDemand;
			priceSupply = priceSupply/sumOfferSupply;
		}
		
		// Lege nun Gesamtstrafe für einzelne Angebote fest
		double sumDeviationDemand = 0;
		double sumDeviationSupply = 0;
		for (int i=0; i<numSlots; i++) {
			if (deviation[i] != 0) {
				if (Math.abs(deviation[i]) == Math.abs(valuesOfferDemand[i]) + Math.abs(valuesOfferSupply[i])) {
					sumDeviationDemand += Math.abs(valuesOfferDemand[i]);
					sumDeviationSupply += Math.abs(valuesOfferSupply[i]);
				}				
				else {
					if (Math.abs(valuesOfferDemand[i]) > Math.abs(valuesOfferSupply[i])) {
						sumDeviationDemand += Math.abs(deviation[i]);
					}
					else {
						sumDeviationSupply += Math.abs(deviation[i]);
					}
				}		
			}
		}
		System.out.println("Gesamtstrafe Demand: " +sumDeviationDemand);
		System.out.println("Gesamtstrafe Supply: " +sumDeviationSupply);
		sumDeviationDemand = sumDeviationDemand*eexPrice;
		sumDeviationSupply = sumDeviationSupply*eexPrice;
		System.out.println("Gesamtstrafe Demand Preis: " +sumDeviationDemand);
		System.out.println("Gesamtstrafe Supply Preis: " +sumDeviationSupply);
		
		// Berechne Strafe pro kWh und füge sie zum Preis hinzu
		priceDemand = priceDemand - sumDeviationDemand/sumOfferDemand;
		priceSupply = priceSupply - sumDeviationSupply/sumOfferSupply;
		System.out.println("Price Demand: " +priceDemand);
		System.out.println("Price Supply: " +priceSupply);
		
		System.out.println("Demand zahlt: " +priceDemand*sumOfferDemand);
		System.out.println("Supply erhält: " +priceSupply*sumOfferSupply);
		System.out.println("BKV bekommt: " + (priceSupply*sumOfferSupply+priceDemand*sumOfferDemand));
	}
}
