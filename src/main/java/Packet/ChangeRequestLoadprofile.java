package Packet;

import java.util.UUID;

public class ChangeRequestLoadprofile {
	UUID offer;
	double[] change;
	
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
}
