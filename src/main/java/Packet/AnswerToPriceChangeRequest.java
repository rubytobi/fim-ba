package Packet;

import java.util.UUID;

public class AnswerToPriceChangeRequest {
	private UUID consumer;
	private double newPrice;

	public AnswerToPriceChangeRequest(UUID consumer, double newPrice) {
		this.consumer = consumer;
		this.newPrice = newPrice;
	}
	
	public AnswerToPriceChangeRequest() { 
		// dummy 
	}

	public UUID getConsumer() {
		return consumer;
	}

	public double getNewPrice() {
		return newPrice;
	}

}
