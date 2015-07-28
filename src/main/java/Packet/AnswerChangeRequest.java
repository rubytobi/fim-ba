package Packet;

import java.util.UUID;

public class AnswerChangeRequest {
	private UUID uuidChangeRequest;
	private double[] possibleChanges;
	private double price;
	
	public AnswerChangeRequest (UUID uuidCR, double[] possibleChanges, double price) {
		this.uuidChangeRequest = uuidCR;
		this.possibleChanges = possibleChanges;
		this.price = price;
	}
	
	public double[] getPossibleChanges() {
		return possibleChanges;
	}

	public double getPrice() {
		return price;
	}
	
	public UUID getUuidChangeRequest() {
		return uuidChangeRequest;
	}
	
}
