package Packet;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class OfferNotification {
	private String location = null;
	private String referenceOffer = null;

	public OfferNotification() {
		// dummy konstruktor
	}

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

	@JsonIgnore
	public boolean isFirstOffer() {
		return referenceOffer == null;
	}
}
