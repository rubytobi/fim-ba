package Packet;

import java.util.UUID;

public class EndOfNegotiation {
	private UUID negotiation;
	private double newPrice1, newPrice2;
	private boolean successful;
	
	public EndOfNegotiation(UUID negotiation, double newPrice1, double newPrice2, boolean successful) {
		this.negotiation = negotiation;
		this.newPrice1 = newPrice1;
		this.newPrice2 = newPrice2;
		this.successful = successful;
	}
	
	public UUID getNegotiation() {
		return negotiation;
	}
	
	public double getNewPrice1() {
		return newPrice1;
	}
	
	public double getNewPrice2() {
		return newPrice2;
	}
	
	public boolean getSuccessful() {
		return successful;
	}
}
