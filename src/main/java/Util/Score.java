package Util;

import java.util.UUID;

import Entity.Offer;

public class Score implements Cloneable {
	private Offer marketplace;
	private Offer own;
	private boolean hasReceived = false;
	private boolean hasChangeRequest = false;
	private Offer received;
	private Offer changeRequest;
	private Offer merge = null;
	private UUID uuid = UUID.randomUUID();

	public boolean equals(Score s) {
		return uuid.equals(s.getUUID());
	}

	public UUID getUUID() {
		return uuid;
	}

	public Score(Offer merge, Offer marketplace, Offer own, Offer received, Offer changeRequest) {
		this.merge = merge;
		this.marketplace = marketplace;
		this.own = own;
		this.received = received;
		this.changeRequest = changeRequest;

		if (received != null) {
			hasReceived = true;
		}

		if (changeRequest != null) {
			hasChangeRequest = true;
		}
	}

	public Offer getMerge() {
		return merge;
	}

	public Offer getOwn() {
		return this.own;
	}

	public Offer getMarketplace() {
		return this.marketplace;
	}

	public String toString() {
		String score = "Score [marketplace=";
		if (marketplace != null)
			score += marketplace.getUUID();
		score += ",own=";
		if (own != null)
			score += own.getUUID();
		score += ",received=";
		if (received != null)
			score += received.getUUID();
		score += "]";
		return score;
	}

	public double[] getDelta() {
		double[] delta = new double[4];

		for (int i = 0; i < 4; i++) {
			delta[i] = Math
					.abs(marketplace.getAggLoadprofile().getValues()[i] - merge.getAggLoadprofile().getValues()[i]);
		}

		return delta;
	}

	@Override
	public Score clone() {
		return new Score(merge, marketplace, own, received, changeRequest);
	}

	public void setReceivedOffer(Offer receivedOffer) {
		if (receivedOffer == null) {
			return;
		}

		hasReceived = true;
		this.received = receivedOffer;
	}

	public double getLoadprofileDeviation() {
		return marketplace.getAggLoadprofile().chargeDeviationOtherProfile(merge.getAggLoadprofile());
	}

	public double getPriceDeviation() {
		return Math.abs(marketplace.getPriceSugg() - merge.getPriceSugg());
	}

	public boolean hasReceivedOffer() {
		return hasReceived;
	}

	public boolean hasChangeRequest() {
		return hasChangeRequest;
	}
}
