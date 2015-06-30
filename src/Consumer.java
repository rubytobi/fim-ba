import java.util.*;

public class Consumer {
	// Aktuelles Angebot an der Börse
	Offer offer;
	
	// Anzahl der 15-Minuten-Slots für ein Lastprofil
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
	
	// Prüfe, ob Angebot aggregiert mit Lastprofil angenommen werden soll
	private boolean testOffer (Offer offerReceived, boolean average) {
		// Prüfe, ob erhaltenes Angebot noch gültig
		if (!offerReceived.getStatus()) {
			return false;
		}
		Loadprofile loadprofileReceived = offerReceived.getAggLoadprofile();
		
		// Prüfe, ob Zeitfenster von erhaltenem und aktuellem Angebot übereinstimmen
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
			 * Hole billigstes Angebot von Börse: Loadprofile cheapProfile
			 * return testOfferOtherProfile(aggLoadprofile, cheapProfile);
			 */
			return false;
		}
	}
	
	// Angebot ist Antwort auf eigenes Angebot: Prüfe, ob es angenommen werden soll
	private boolean acceptOffer (Offer offerReceived, boolean average) {
		// Prüfe, ob bereits gebunden
		if (!testOfferDate(offerReceived.getAggLoadprofile())) {
			return false;
		}

		// TODO Preisüberprüfung
		
		if (average) {
			return testOfferAverage(offerReceived.getAggLoadprofile());
		}
		else {
			/* TODO
			 * Hole billigstes Angebot von Börse: Loadprofile cheapProfile
			 * return testOfferOtherProfile(aggLoadprofile, cheapProfile);
			 */
			return false;
		}
	}
	
	// Wenn ein Angebot eintrifft, wird entschieden, was gemacht werden soll
	public void manageOffer (Offer offerReceived) {
		// Prüfe, ob Consumer schon Teil des Angebots
		
		// TODO wie wird geprüft, ob Antwort auf eigenes Angebot oder bereits verhandeltes Angebot?
		// TODO wie erfahren alle Teilnehmer, wenn zu aggregiertem Angebot noch ein Consumer hinzugefügt wurde?
		
		if (offerReceived.getAllConsumers().contains(this)) {
		// Wenn Consumer Teil des Angebots ist, ist das Angebot eine Antwort und es muss Zu- oder Abgesagt werden			
			if (acceptOffer (offerReceived, true)) {
				// TODO Schicke Zusage und warte auf Bestätigung
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
		
		// Wenn der Consumer nicht Teil des Angebots ist, muss das aggregierte Lastprofil geprüft und ggf. ein neues Angebot versendet werden
		else {			
			// Wenn aggregiertes Lastprofil Verbesserung verspricht, erstelle es und schicke es an Consumer, von dem das Angebot war
			if (testOffer (offerReceived, true)) {
				// TODO Erstelle neues Angebot und schicke es "zurück"
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
			// TODO Schicke Bestätigung
			
			this.offer.setStatus(false);
			this.offer = offerToConfirm;
			
			// TODO Angebot verschicken an Börse
			// TODO Angebot verschicken an Teilnehmer
		}
	}
}
