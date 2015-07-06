package Packet;

public class OfferNotification {
	private String location;
	private String referenceOffer;

	public OfferNotification(String location, String referenceOffer) {
		this.location = location;
		this.referenceOffer = referenceOffer;
	}

	public String getLocation() {
		return location;
	}

	public String getReferenceOffer() {
		return referenceOffer;
	}

	public String toString() {
		return "[location=" + location + ",referenceOffer=" + referenceOffer + "]";
	}
}
