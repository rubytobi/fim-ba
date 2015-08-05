package Packet;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import Util.DateTime;
import start.Loadprofile;

public class ChangeRequestLoadprofile {
	UUID offer;
	double[] change;

	protected ChangeRequestLoadprofile() {
		// dummy
	}

	public ChangeRequestLoadprofile(UUID offer, double[] change) {
		this.offer = offer;
		this.change = change;
	}

	public UUID getOffer() {
		return offer;
	}

	public double[] getChange() {
		return change;
	}

	@JsonIgnore
	public boolean isZero() {
		for (double d : change) {
			if (d != 0) {
				return false;
			}
		}
		return true;
	}

	public Loadprofile toLoadprofile() {
		return new Loadprofile(change, DateTime.currentTimeSlot(), 0.0);
	}
}
