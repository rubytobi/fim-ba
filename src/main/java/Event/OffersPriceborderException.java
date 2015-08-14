package Event;

public class OffersPriceborderException extends Exception {
	public OffersPriceborderException() {
		super("Preisgrenzen der Angebote sind nicht miteinander vereinbar.");
	}
}
