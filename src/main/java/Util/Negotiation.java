package Util;

import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import Container.NegotiationContainer;
import Entity.Offer;
import Packet.AnswerToOfferFromMarketplace;
import Packet.AnswerToPriceChangeRequest;
import Packet.EndOfNegotiation;
import start.Application;
import start.Marketplace;

public class Negotiation {
	private Offer offer1, offer2;
	private int round1, round2;
	private boolean finished1, finished2, closed;
	private double currentPrice1, currentPrice2;
	private double priceToAchieve1, priceToAchieve2;
	private double sumLoadprofile1, sumLoadprofile2, currentSum;
	private double priceAskedFor1, priceAskedFor2;
	private UUID uuid;
	private int maxRounds = 2;
	private Marketplace marketplace;
	
	
	public Negotiation (Offer offer1, Offer offer2, double priceToAchieve1, double priceToAchieve2, double sumLoadprofile1, double sumLoadprofile2) {
		this.offer1 = offer1;
		this.offer2 = offer2;
		this.uuid = UUID.randomUUID();
		this.round1 = 0;
		this.round2 = 0;
		this.sumLoadprofile1 = sumLoadprofile1;
		this.sumLoadprofile2 = sumLoadprofile2;
		this.currentPrice1 = offer1.getPrice();
		this.currentPrice2 = offer2.getPrice();
		this.priceToAchieve1 = priceToAchieve1;
		this.priceToAchieve2 = priceToAchieve2;
		this.priceAskedFor1 = 0;
		this.priceAskedFor2 = 0;
		this.finished1 = false;
		this.finished2 = false;
		this.closed = false;
		this.currentSum = sumLoadprofile1*offer1.getPrice() +  sumLoadprofile2*offer2.getPrice();
		this.marketplace = Marketplace.instance();
		
		// Füge Negotiation zu Container hinzu
		NegotiationContainer container = NegotiationContainer.instance();
		container.add(this);
		
		// Sende erste Preisanfrage
		sendPriceRequest(offer1.getUUID());
		sendPriceRequest(offer2.getUUID());
	}
	
	/**
	 * Gibt eine Beschreibung der Verhandlung auf der Console aus.
	 * @return
	 */
	public void negotiationToString() {
		System.out.println("Negotiation " +uuid+ " CurrentSum: " +currentSum+ " Confirmed: " +closed);
		System.out.println("Offer1: " +offer1.getUUID()+ " Runde: " + round1 + " Summe LP: " 
				+sumLoadprofile1+ " aktueller Preis: " + currentPrice1+ " zu erreichender Preis: " 
				+priceToAchieve1+ " erfragter Preis: " +priceAskedFor1+ " Finished: " +finished1);
		System.out.println("Offer2: " +offer2.getUUID()+ " Runde: " + round2 + " Summe LP: "
				+sumLoadprofile2+ " aktueller Preis: " + currentPrice1 + " zu erreichender Preis: "
				+priceToAchieve2+ " erfragter Preis: " + priceAskedFor2+ " Finished: " +finished2);
	}
	
	/**
	 * Liefert die an der Negotiation beteiligten Angebote
	 * @return Array mit Offer1 (0) und Offer2 (1) der Negotiation
	 */
	public Offer[] getOffers() {
		Offer[] offers = new Offer[2];
		offers[0] = offer1;
		offers[1] = offer2;
		return offers;
	}
	
	/**
	 * Liefert die UUID der Negotiation
	 * @return UUID der Negotiation
	 */
	public UUID getUUID() {
		return uuid;
	}
	
	/** 
	 * Sendet eine Preisanfrage an das übergebene Angebot.
	 * Hierfuer wird zuerst mit Hilfe einer Zufallszahl festgelegt, welcher
	 * Preis angefragt wird. 
	 * Dabei ist die angefragte Preisänderung größer als die eigentlich 
	 * benötigte Änderung, aber kleiner als die davor angefragte Preisänderung.
	 * @param offer Angebot, an das Anfrage nach Preisänderung gesendet wird
	 */
	private void sendPriceRequest(UUID offer) {
		System.out.println("***sendPriceRequest***");
		double random = 1+Math.random()*2;
		double priceRequest;
		Offer currentOffer;
		if (offer.equals(offer1.getUUID())) {
			if (round1 == maxRounds) {
				informMarketplace(false);
			}
			currentOffer = offer1;
			if (round1 == 0) {
				priceAskedFor1 = currentPrice1 + (priceToAchieve1-currentPrice1)*random;
				priceRequest = priceAskedFor1;
			}
			else {
				double diff = priceAskedFor1 - priceToAchieve1;
				if (diff < 0) {
					priceAskedFor1 = priceAskedFor1 + Math.random()*Math.abs(diff);
				}
				else {
					priceAskedFor1 = priceToAchieve1 + Math.random()*Math.abs(diff);
				}
				priceRequest = priceAskedFor1;
			}
			round1++;
			
		}
		else {
			if (round2 == maxRounds) {
				informMarketplace(false);
			}
			currentOffer = offer2;

			if (round2 == 0) {
				priceAskedFor2 = priceToAchieve2 + (priceToAchieve2 - currentPrice2)*random;
				priceRequest = priceAskedFor2;
			}
			else {
				double diff = priceAskedFor2 - priceToAchieve2;
				if (diff < 0) {
					priceAskedFor2 = priceAskedFor2 + Math.random()*Math.abs(diff);
				}
				else {
					priceAskedFor2 = priceToAchieve2 + Math.random()*Math.abs(diff);
				}
				priceRequest = priceAskedFor2;
			}
			round2++;
		}
		System.out.println("An: " +currentOffer.getAuthor()+ " Preis: " +priceRequest);		
		
		// Sende Anfrage mit priceRequest an consumer
		AnswerToOfferFromMarketplace answerOffer = new AnswerToOfferFromMarketplace(offer, priceRequest);
		
		RestTemplate rest = new RestTemplate();
		HttpEntity<AnswerToOfferFromMarketplace> entity = new HttpEntity<AnswerToOfferFromMarketplace>(answerOffer, Application.getRestHeader());
	
		String url = "http://localhost:8080/consumers/" + currentOffer.getAuthor() + "/offers/" + offer
				+ "/negotiation/" +uuid+ "/priceChangeRequest";
	
		try {
			ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
		} catch (Exception e) {
		}
				
	}
	
	/**
	 * Beendet den Verhandlungsstrang des Angebots, in dem der übergebene Consumer Autor ist.
	 * Anschließend wird geprüft, ob beide Verhandlungsstränge beendet sind.
	 * Wenn ja, wird das Ende der Verhandlung dem Marktplatz gemeldet.
	 * @param consumer Consumer, dessen Angebot nicht mehr weiter verhandelt wird
	 */
	private void finishNegotiationOffer(UUID consumer) {
		if (consumer.equals(offer1.getAuthor())) {
			finished1 = true;
		}
		else {
			finished2 = true;
		}
		if (finished1 && finished2) {
			//Teile Marktplatz mit, dass Verhandlung nicht erfolgreich beendet wurde
			informMarketplace(false);
		}
	}
	
	/**
	 * Behandelt die Antwort eines Consumers mit neuem Preis.
	 * Wenn noch eine Änderung notwendig ist, wird currentSum aktualisiert.
	 * Ist currentSum >= 0 werden die exakten Preise berechnet und die Verhandlung damit beendet.
	 * Ansonsten wird geprüft, ob die maximale Anzahl an Verhandlungsrunden erreicht ist. Wenn ja
	 * erfolgt der Methodenaufruf von finishNegotiationOffer, wenn nein wird eine erneute 
	 * Preisanfrage versendet.
	 * @param consumer Consumer, von dem die Antwort kam
	 * @param newPrice Neuen Preis, den der Consumer eingehen kann
	 */
	public void receiveAnswer (AnswerToPriceChangeRequest answer) {
		UUID consumer = answer.getConsumer();
		double newPrice = answer.getNewPrice();
		System.out.println("***receiveAnswer***");
		if (closed) {
			return;
		}
		UUID offer;
		boolean answerFromOffer1 = consumer.equals(offer2.getAuthor());
		if (answerFromOffer1) {
			System.out.println("Offer1, neuer Preis: " +newPrice);
			offer = offer1.getUUID();
			// Schicke erneute Anfrage, wenn neuer Preis schlechter als alter Preis
			if (Math.abs(priceToAchieve1-newPrice) > Math.abs(priceToAchieve1-currentPrice1)) {
				System.out.println("Preis schlechter als alter Preis!");
				sendPriceRequest(offer);
				return;
			}
			System.out.println(currentSum);
		}
		else {
			System.out.println("Offer2, neuer Preis: " +newPrice);
			offer = offer2.getUUID();
			// Schicke erneute Anfrage, wenn neuer Preis schlechter als alter Preis
			if (Math.abs(priceToAchieve2 - newPrice) > Math.abs(priceToAchieve2-currentPrice2)) {
				sendPriceRequest(offer);
			}
		}
		
		updateCurrentSum(offer, newPrice);
		if (currentSum >= 0) {
			// Berechne extakte Preise
			chargeExactPrices();
			
			// Teile Marktplatz mit, dass Verhandlung erfolgreich beendet wurde
			informMarketplace(true);
		}
		else {
			if (answerFromOffer1 && round1 >= maxRounds
					|| (!answerFromOffer1) && round2 >= maxRounds) {
				finishNegotiationOffer(consumer);
			}
			else {
				chargeNewPricesToAchieve();
				sendPriceRequest(offer);
			}
		}
	}
	
	/** 
	 * Berechnet, welche Preise nun von beiden Seiten idealerweise 
	 * erreicht werden sollen und legt das Ergebnis in den Variablen
	 * priceToAchieve1 bzw. priceToAchieve2 ab.
	 */
	private void chargeNewPricesToAchieve() {
		// Lege zu erreichende Preise für beide Angebote fest
		if (sumLoadprofile1 + sumLoadprofile2 == 0) {
			priceToAchieve1 = (currentPrice1 + currentPrice2) / 2;
			priceToAchieve2 = priceToAchieve1;
		} else {
			// Ermittle Gesamtpreis für beide Angebote
			double price1 = sumLoadprofile1 * currentPrice1;
			double price2 = sumLoadprofile2 * currentPrice2;
			System.out.println("Einzelpreis: " +offer1.getPrice()+ ", " +offer2.getPrice());
			System.out.println("Gesamtpreis: " +price1+ ", " +price2);
			// Ermittle Mittelwert von Betrag des Gesamtpreises beider Angebote
			double price = (Math.abs(price1) + Math.abs(price2)) / 2;
			// Weise den Gesamtpreisen den jeweiligen Mittelwert zu
			if (sumLoadprofile1 > 0) {
				price1 = price;
			} else {
				price1 = -price;
			}
			if (sumLoadprofile2 > 0) {
				price2 = price;
			} else {
				price2 = -price;
			}
			System.out.println("Mittelwert: " +price1+ ", " +price2);
				// Berechne Preise pro kWh für Angebote
			priceToAchieve1 = price1 / sumLoadprofile1;
			priceToAchieve2 = price2 / sumLoadprofile2;
		}
	}
	
	/**
	 * Berechnet die neue Gesamtsumme der Angebote, wenn ein Angebot
	 * einer Preisänderung zugestimmt hat und legt das Ergebnis in
	 * der Variable currentSum ab.
	 * @param offer Angebot, das einer Preisänderung zugestimmt hat
	 * @param newPrice Preis, dem das Angebot zugestimmt hat
	 */
	private void updateCurrentSum(UUID offer, double newPrice) {
		if (offer.equals(offer1.getUUID())) {
			System.out.println("Offer1 neuer Preis: " +newPrice);
			currentPrice1 = newPrice;
		}
		if (offer.equals(offer2.getUUID())) {
			System.out.println("Offer2 neuer Preis: " +newPrice);
			currentPrice2 = newPrice;
		}
		currentSum = sumLoadprofile1*currentPrice1 + sumLoadprofile2*currentPrice2;
	}
	
	/**
	 * Berechnet exakte Preise, wenn Summe >= 0, so dass Summe = 0.
	 * Die neuen Preise werden in currentPrice1 und currentPrice2 gespeichert.
	 * @return Array mit neuen Preisen für Offer1 (0) und Offer2 (1)
	 */
	private void chargeExactPrices() {
		System.out.println("***chargeExactPrices***");
		// Berechne neue, extakte Preise
		double newPrice1 = sumLoadprofile1*currentPrice1;
		double newPrice2 = sumLoadprofile2*currentPrice2;
		
		if (! (Math.abs(newPrice1) == Math.abs(newPrice2))) {
			if (Math.abs(newPrice1) > Math.abs(newPrice2) && newPrice1 > 0
					|| Math.abs(newPrice1) < Math.abs(newPrice2) && newPrice1 < 0) {
				newPrice1 = Math.abs(newPrice2/sumLoadprofile1);
				newPrice2 = Math.abs(newPrice2/sumLoadprofile2);
			}
			else {
				newPrice2 = Math.abs(newPrice1/sumLoadprofile2);
				newPrice1 = Math.abs(newPrice1/sumLoadprofile1);
			}
		}
		currentPrice1 = newPrice1;
		currentPrice2 = newPrice2;
		System.out.println(currentPrice1*sumLoadprofile1 + currentPrice2*sumLoadprofile2);
	}	
	
	/**
	 * Informiert den Marktplatz über den Ausgang der Verhandlung.
	 * Diese Information beinhaltet die UUID der Verhandlung, die neuen Preise
	 * der beiden Angebote und ob die Verhandlung erfolgreich beendet wurde.
	 * @param successful Gibt an, ob die Verhandlung erfolgreich beendet wurde.
	 */
	public void informMarketplace (boolean successful) {
		// Schließe Verhandlung
		closed = true;
		
		// Entferne Verhandlung vom Container
		// TODO Sinnvoll? Wenn Consumer noch auf ausstehende ChangeRequest antwortet? Aber wann sonst?
		NegotiationContainer container = NegotiationContainer.instance();
		container.delete(uuid);
		
		// Informiere Marktplatz über Ende der Verhandlung
		EndOfNegotiation end = new EndOfNegotiation(uuid, currentPrice1, currentPrice2, successful);
		
		RestTemplate rest = new RestTemplate();
		HttpEntity<EndOfNegotiation> entity = new HttpEntity<EndOfNegotiation>(end, Application.getRestHeader());
	
		String url = "http://localhost:8080/marketplace/endOfNegotiation/";
	
		try {
			ResponseEntity<Void> response = rest.exchange(url, HttpMethod.POST, entity, Void.class);
		} catch (Exception e) {
		}
	}
}
