package start;

import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.UUID;
import java.util.ArrayList;

import Entity.Fridge;
import Entity.Offer;
import Entity.BHKW;
import Packet.AnswerToOfferFromMarketplace;
import Util.DateTime;
import start.Marketplace;
import Util.PossibleMatch;
import Util.Negotiation;

public class Main {
	public static void main(String[] args) {
		double[] changesKWH = {-1, 5, -4,- 3};
		double[] planned = {6, 6, 6, 6};
		double levelHeatReservoir = 5;
		double sizeHeatReservoir = 10;
		double chpCoefficient = 1;
		double maxLoad = 10;
		double priceFuel = 0.2;
		double consFuelPerKWh = 1;
		
		int numSlots = 4;
		
		double price = 0;
		
		for (int i=0; i<numSlots; i++) {
			double value = changesKWH[i];
			System.out.println(i+ ". Änderung gewünscht: " +value);

			double powerGained = 0;
						
			if (value < 0) {
				// Prüfe, dass produzierte Last nicht unter 0 fällt
				if (planned[i] + value < 0) {
					System.out.println("Produzierte Last fällt unter null");
					value = -planned[i];
				}
				// Berechne Wärme, die nun nicht mehr produziert,
				// aber benötigt wird und daher vom Wärmespeicher
				// bezogen werden muss
				double heat = Math.abs(value/chpCoefficient);
				if (levelHeatReservoir-heat > 0) {
					System.out.println("Wärme die wegfällt: " +heat);
					levelHeatReservoir -= heat;
					System.out.println("Neuer Füllstand: " +levelHeatReservoir);
					powerGained = value;
				}
				else {
					// Hole so viel wie möglich aus Speicher
					System.out.println("Leere Speicher");
					powerGained = -levelHeatReservoir;
					levelHeatReservoir = 0;
				}
			}
			if (value > 0) {
				// Prüfe, das maximale Last nicht überschritten wird
				if (planned[i] + value > maxLoad) {
					System.out.println("Maximale Last überschritten");
					value = maxLoad-planned[i];
				}
				// Nehme Strom möglichst vom Wärmespeicher
				if (levelHeatReservoir > 0) {
					if (levelHeatReservoir >= value) {
						// TODO Wie Verhältnis abgeführte Wärme - daraus erzeugter Strom
						levelHeatReservoir -= value;
						powerGained = value;
					}
					else {
						powerGained = levelHeatReservoir;
						levelHeatReservoir = 0;
					}
				}
				
				if (powerGained != value) {
					// Produziere restlichen Strom, speichere dabei
					// als Nebenprodukt produzierte Wärme und 
					// berechne anfallende Kosten
					double powerProduced = value-powerGained;
					double heatProduced = powerProduced*chpCoefficient;
					System.out.println("Wärme produziert: "+heatProduced);
					if (levelHeatReservoir+heatProduced <= sizeHeatReservoir) {
						levelHeatReservoir += heatProduced;
					}
					else {
						powerProduced = sizeHeatReservoir - levelHeatReservoir;
						levelHeatReservoir = sizeHeatReservoir;
					}
					// Berechne, wie viel Energie tatsächlich produziert werden konnte
					powerGained += powerProduced;
	
					// Berechne Preis für zusätzlich benötigten Brennstoff
					price += Math.abs(powerProduced*consFuelPerKWh*priceFuel);
				}
			}
			changesKWH[i] = powerGained;
			System.out.println(i+". Änderung wirklich: " +changesKWH[i]);
			System.out.println("Füllstand Wärmespeicher: " +levelHeatReservoir);
		}
		System.out.println("Preis: " +price);
	}
}
