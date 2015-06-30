import java.util.*;

public class Consumer {
	// Aktuelles Angebot
	Offer offer;
	
	// Anzahl der 15-Minuten-Slots für ein Lastprofil
	int numSlots = 4;
	
	// Aktuelles Stundenlastprofil (evtl. auch schon aggregiert mit anderen Teilnehmern)
	Loadprofile loadprofile;
	
	public int getNumSlots() {
		return numSlots;
	}
	
	public double[] chargeValuesLoadprofile(double[] scheduleMinutes) {
		double[] valuesLoadprofile = new double[numSlots];
		double summeMin = 0;
		double summeHour = 0;
		int j = 0;
		
		for (int i=0; i<numSlots*15; i++) {
			summeMin = summeMin + scheduleMinutes[i];
			if((i+1)%15 == 0 && i!=0) {
				valuesLoadprofile[j] = summeMin;
				summeHour = summeHour+valuesLoadprofile[j];
				j++;
				System.out.println("Verbrauch " +j+ ". 15 Minuten: " +summeMin);
				summeMin = 0;
			}
		}
		return valuesLoadprofile;
	}
	
	private Loadprofile generateAggLoadprofile(Offer offer) {
		Loadprofile aggLoadprofile = new Loadprofile(loadprofile, offer.getAggLoadprofile());
		return aggLoadprofile;
	}
	
	private boolean testOfferDate (Offer offer) {
		Date dateOffer = offer.getAggLoadprofile().getDate();
		Date dateLoadprofile = loadprofile.getDate();
		
		if (dateOffer == dateLoadprofile) {
			return true;
		}
		else {
			return false;
		}
	}
	
	private boolean testOfferAverage (Offer offer) {
		Loadprofile aggLoadprofile = generateAggLoadprofile(offer);
		
		double deviation = loadprofile.chargeDeviationAverage();
		double aggDeviation = aggLoadprofile.chargeDeviationAverage();
		
		if (aggDeviation < deviation) {
			return true;
		}
		else {
			return false;
		}
	}
	
	private boolean testOfferOtherProfile (Offer offer, Loadprofile otherProfile) {
		Loadprofile aggLoadprofile = generateAggLoadprofile(offer);
		
		double deviation = loadprofile.chargeDeviationOtherProfile(otherProfile);
		double aggDeviation = aggLoadprofile.chargeDeviationOtherProfile(otherProfile);
		
		if (aggDeviation < deviation) {
			return true;
		}
		else {
			return false;
		}
	}
	
}
