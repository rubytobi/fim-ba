package Util;

import Entity.Offer;
import Event.OffersPriceborderException;

public class Score implements Cloneable {
	private Offer marketplace;
	private Offer own;
	private Double score = null;
	private boolean hasReceived = false;
	private boolean hasChangeRequest = false;
	private Offer received;
	private Offer changeRequest;
	private Offer merge = null;

	public Score(Offer marketplace, Offer own, Offer received, Offer changeRequest) {
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
		if (merge == null) {
			merge = own;

			if (hasReceived) {
				try {
					merge = new Offer(merge, received);
				}
				catch (OffersPriceborderException e) {
					// TODO Was passiert, wenn Exception eintritt?
				}
			}

			if (hasChangeRequest) {
				try {
					merge = new Offer(merge, changeRequest);
				}
				catch (OffersPriceborderException e) {
					// TODO Was passiert, wenn Exception eintritt?
				}
			}
		}

		return merge;
	}

	public double getScore() {
		if (score == null) {
			score = marketplace.getAggLoadprofile().chargeDeviationOtherProfile(getMerge().getAggLoadprofile());
		}

		return score;
	}

	public Offer getOwn() {
		return this.own;
	}

	public Offer getMarketplace() {
		return this.marketplace;
	}

	public String toString() {
		return "Score [score=" + getScore() + ",marketplace=" + marketplace.getUUID().toString() + ",tempOffer="
				+ own.getUUID().toString() + "]";
	}

	public double[] getDelta() {
		double[] delta = new double[4];

		for (int i = 0; i < 4; i++) {
			delta[i] = marketplace.getAggLoadprofile().getValues()[i] - own.getAggLoadprofile().getValues()[i];
		}

		return delta;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Score) {
			if (((Score) obj).getOwn().getUUID().equals(own.getUUID())
					&& ((Score) obj).getMarketplace().getUUID().equals(marketplace.getUUID())) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Score clone() {
		return new Score(marketplace, own, received, changeRequest);
	}

	public void setReceivedOffer(Offer receivedOffer) {
		if (receivedOffer == null) {
			return;
		}

		hasReceived = true;
		this.received = receivedOffer;
	}

	public double getLoadprofileDeviation() {
		return marketplace.getAggLoadprofile().chargeDeviationOtherProfile(getMerge().getAggLoadprofile());
	}

	public double getPriceDeviation() {
		return Math.abs(marketplace.getPriceSugg() - getMerge().getPriceSugg());
	}
}
