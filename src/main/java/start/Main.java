package start;

import Entity.Offer;

public class Main {
	public static void main(String[] args) {
		double[] valuesOffer1 = {0.0, 3.0, 5.0, -2.0};
		double[] valuesOffer2 = {0.0, -2.5, -5.5, 2.0};
		double priceOffer1 = 0.5;
		double priceOffer2 = 0.8;
		double[] deviation = null;
	}
	
	private void mergeOffers (double[] valuesOffer1, double[] valuesOffer2, 
			double priceOffer1, double priceOffer2, double[] deviation) {		
		Offer offerDemand = null, offerSupply = null;
		
		double eexPrice = 20;
		int numSlots = 4;
		
		double sumOffer1 = 0;
		double sumOffer2 = 0;
		double sumOfferDemand, sumOfferSupply, priceOfferDemand, priceOfferSupply;
		double[] valuesOfferDemand, valuesOfferSupply;
		for (int i=0; i<4; i++) {
			sumOffer1 += valuesOffer1[i];
			sumOffer2 += valuesOffer2[i];
		}
		System.out.println("Summe Offer1: " +sumOffer1);
		System.out.println("Summe Offer2: " +sumOffer2);
		if (sumOffer1<0 && sumOffer2>0) {
			System.out.println("Offer Demand: Offer1, Offer Supply: Offer2");
			sumOfferDemand = sumOffer1;
			valuesOfferDemand = valuesOffer1;
			priceOfferDemand = priceOffer1;
			
			sumOfferSupply = sumOffer2;
			valuesOfferSupply = valuesOffer2;
			priceOfferSupply = priceOffer2;
		}
		else if (sumOffer1 > 0 && sumOffer2 < 0) {
			System.out.println("Offer Demand: Offer1, Offer Supply: Offer2");
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
			priceDemand = (offerDemand.getPrice()+offerSupply.getPrice())/2;
			priceSupply = priceDemand;
		}
		else {
			// Ermittle Gesamtpreis für beide Angebote
			priceDemand = sumOfferDemand*offerDemand.getPrice();
			priceSupply = sumOfferSupply*offerSupply.getPrice();
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
				if (deviation[i] == valuesOfferDemand[i] + valuesOfferSupply[i]) {
					sumDeviationDemand += Math.abs(valuesOfferDemand[i]);
					sumDeviationSupply += Math.abs(valuesOfferSupply[i]);
				}				
				else {
					if (Math.abs(valuesOfferDemand[i]) > Math.abs(valuesOfferSupply[i])) {
						sumDeviationDemand += Math.abs(valuesOfferDemand[i]);
					}
					else {
						sumDeviationSupply += Math.abs(valuesOfferSupply[i]);
					}
				}		
			}
		}
		sumDeviationDemand = sumDeviationDemand*eexPrice;
		sumDeviationSupply = sumDeviationSupply*eexPrice;
		
		// Berechne Strafe pro kWh und füge sie zum Preis hinzu
		priceDemand = priceDemand + sumDeviationDemand/sumOfferDemand;
		priceSupply = priceSupply - sumDeviationSupply/sumOfferSupply;
	}
}
