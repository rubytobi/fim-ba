import java.util.*;

public class Consumer {
	// Aktuelles Angebot an der B�rse
	Offer offer;
	
	// Anzahl der 15-Minuten-Slots f�r ein Lastprofil
	int numSlots = 4;
	
	// Aktuelles Stundenlastprofil (evtl. auch schon aggregiert mit anderen Teilnehmern)
	Loadprofile loadprofile;
	
	public int getNumSlots() {
		return numSlots;
	}
	
	private boolean testOfferDate (Loadprofile loadprofileReceived) {
		Date dateReceived = loadprofileReceived.getDate();
		Date dateLoadprofile = loadprofile.getDate();
		
		return (dateReceived == dateLoadprofile);
	}
	
	private boolean testOfferAverage (Loadprofile aggLoadprofile) {
		double deviation = loadprofile.chargeDeviationAverage();
		double aggDeviation = aggLoadprofile.chargeDeviationAverage();
		
		return (aggDeviation < deviation);
	}
	
	private boolean testOfferOtherProfile (Loadprofile aggLoadprofile, Loadprofile otherProfile) {
		double deviation = loadprofile.chargeDeviationOtherProfile(otherProfile);
		double aggDeviation = aggLoadprofile.chargeDeviationOtherProfile(otherProfile);
		
		return (aggDeviation < deviation);
	}
	
	// Pr�fe, ob Angebot aggregiert mit Lastprofil angenommen werden soll
	private boolean testOffer (Offer offerReceived, boolean average) {
		// Pr�fe, ob erhaltenes Angebot noch g�ltig
		if (!offerReceived.getStatus()) {
			return false;
		}
		Loadprofile loadprofileReceived = offerReceived.getAggLoadprofile();
		
		// Pr�fe, ob Zeitfenster von erhaltenem und aktuellem Angebot �bereinstimmen
		if (testOfferDate(loadprofileReceived)) {
			return false;
		}
		
		// Erstelle aggregiertes Lastprofil
		Loadprofile aggLoadprofile = new Loadprofile(loadprofile, loadprofileReceived);
		
		if (average) {
			return testOfferAverage(aggLoadprofile);
		}
		else {
			/* TODO
			 * Hole billigstes Angebot von B�rse: Loadprofile cheapProfile
			 * return testOfferOtherProfile(aggLoadprofile, cheapProfile);
			 */
			return false;
		}
	}
	
	// Angebot ist Antwort auf eigenes Angebot: Pr�fe, ob es angenommen werden soll
	private boolean acceptOffer (Offer offerReceived, boolean average) {
		// Pr�fe, ob bereits gebunden
		if (!testOfferDate(offerReceived.getAggLoadprofile())) {
			return false;
		}

		// TODO Preis�berpr�fung
		
		if (average) {
			return testOfferAverage(offerReceived.getAggLoadprofile());
		}
		else {
			/* TODO
			 * Hole billigstes Angebot von B�rse: Loadprofile cheapProfile
			 * return testOfferOtherProfile(aggLoadprofile, cheapProfile);
			 */
			return false;
		}
	}
	
	// Wenn ein Angebot eintrifft, wird entschieden, was gemacht werden soll
	public void manageOffer (Offer offerReceived) {
		// Pr�fe, ob Consumer schon Teil des Angebots
		
		// TODO wie wird gepr�ft, ob Antwort auf eigenes Angebot oder bereits verhandeltes Angebot?
		// TODO wie erfahren alle Teilnehmer, wenn zu aggregiertem Angebot noch ein Consumer hinzugef�gt wurde?
		
		if (offerReceived.getAllConsumers().contains(this)) {
		// Wenn Consumer Teil des Angebots ist, ist das Angebot eine Antwort und es muss Zu- oder Abgesagt werden			
			if (acceptOffer (offerReceived, true)) {
				// TODO Schicke Zusage und warte auf Best�tigung
				// Wenn Absage erhalten: Abbrechen
				
				// Invalidiere altes Angebot
				this.offer.setStatus(false);
				// Mache erhaltenes Angebot zu aktuellem Angebot
				this.offer = offerReceived;
			}
			else {
				// TODO Schicke Absage
			}
		}
		
		// Wenn der Consumer nicht Teil des Angebots ist, muss das aggregierte Lastprofil gepr�ft und ggf. ein neues Angebot versendet werden
		else {			
			// Wenn aggregiertes Lastprofil Verbesserung verspricht, erstelle es und schicke es an Consumer, von dem das Angebot war
			if (testOffer (offerReceived, true)) {
				// TODO Erstelle neues Angebot und schicke es "zur�ck"
			}
			// Wenn aggregiertes Lastprofil keine Verbesserung verspricht, tue nichts.
		}
	}
	
	// Wenn eine Zusage zu einem Angebot eintrifft
	public void confirmOffer (Offer offerToConfirm) {
		if (!testOfferDate(offerToConfirm.getAggLoadprofile())) {
			// TODO Schicke Absage
		}
		else {
			// TODO Schicke Best�tigung
			
			this.offer.setStatus(false);
			this.offer = offerToConfirm;
			
			// TODO Angebot verschicken an B�rse
			// TODO Angebot verschicken an Teilnehmer
		}
	}
}
