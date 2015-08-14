package Util;

import java.util.UUID;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import Container.NegotiationContainer;
import Entity.Offer;
import Packet.AnswerToOfferFromMarketplace;
import Packet.AnswerToPriceChangeRequest;
import Packet.EndOfNegotiation;
import start.Application;

public class Negotiation {
	private Offer offer1, offer2;
	private int round1, round2;
	private boolean finished1, finished2, closed;
	private boolean demand1, demand2;
	private double currentPrice1, currentPrice2;
	private double sumLoadprofile1, sumLoadprofile2;
	private double minCurrentSum, maxCurrentSum;
	private double acceptedMin1, acceptedMax1;
	private double acceptedMin2, acceptedMax2;
	private UUID uuid;
	private int maxRounds = 3;

	public Negotiation(Offer offer1, Offer offer2, double sumLoadprofile1, double sumLoadprofile2) {
		this.offer1 = offer1;
		this.offer2 = offer2;
		this.uuid = UUID.randomUUID();
		this.round1 = 0;
		this.round2 = 0;
		this.sumLoadprofile1 = sumLoadprofile1;
		this.sumLoadprofile2 = sumLoadprofile2;
		this.currentPrice1 = offer1.getPriceSugg();
		this.currentPrice2 = offer2.getPriceSugg();
		this.finished1 = false;
		this.finished2 = false;
		this.closed = false;
		this.acceptedMin1 = currentPrice1;
		this.acceptedMax1 = currentPrice1;
		this.acceptedMin2 = currentPrice2;
		this.acceptedMax2 = currentPrice2;
		this.demand1 = sumLoadprofile1 < 0;
		this.demand2 = sumLoadprofile2 < 0;
		this.minCurrentSum = sumLoadprofile1 * currentPrice1 + sumLoadprofile2 * currentPrice2;
		this.maxCurrentSum = minCurrentSum;

		// Füge Negotiation zu Container hinzu
		NegotiationContainer container = NegotiationContainer.instance();
		container.add(this);

		// Sende erste Preisanfrage
		sendPriceRequest(offer1.getUUID());
		sendPriceRequest(offer2.getUUID());
	}

	/**
	 * Gibt eine Beschreibung der Verhandlung auf der Console aus.
	 */
	public void negotiationToString() {
		System.out.println("Negotiation " + uuid + " Confirmed: " + closed);
		System.out.println("Offer1: " + offer1.getUUID() + " Runde: " + round1 + " Summe LP: " + sumLoadprofile1
				+ " aktueller Preis: " + currentPrice1 + " Finished: " + finished1);
		System.out.println("Offer2: " + offer2.getUUID() + " Runde: " + round2 + " Summe LP: " + sumLoadprofile2
				+ " aktueller Preis: " + currentPrice1 + " Finished: " + finished2);
	}

	/**
	 * Liefert die an der Negotiation beteiligten Angebote
	 * 
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
	 * 
	 * @return UUID der Negotiation
	 */
	public UUID getUUID() {
		return uuid;
	}

	/**
	 * Sendet eine Preisanfrage an das übergebene Angebot. Hierfuer wird zuerst
	 * mit Hilfe einer Zufallszahl festgelegt, welcher Preis angefragt wird.
	 * Dabei ist die angefragte Preisänderung größer als die eigentlich
	 * benötigte Änderung, aber kleiner als die davor angefragte Preisänderung.
	 * 
	 * @param offer
	 *            Angebot, an das Anfrage nach Preisänderung gesendet wird
	 */
	private void sendPriceRequest(UUID offer) {
		System.out.println("***sendPriceRequest***");
		double priceRequest;
		Offer currentOffer;
		if (offer.equals(offer1.getUUID())) {
			currentOffer = offer1;
			priceRequest = currentPrice2;
			round1++;

		} else {
			currentOffer = offer2;
			priceRequest = currentPrice1;
			round2++;
		}
		System.out.println("An: " + currentOffer.getAuthor() + " Preis: " + priceRequest);

		// Sende Anfrage mit priceRequest an consumer
		AnswerToOfferFromMarketplace answerOffer = new AnswerToOfferFromMarketplace(offer, priceRequest);

		RestTemplate rest = new RestTemplate();
		HttpEntity<AnswerToOfferFromMarketplace> entity = new HttpEntity<AnswerToOfferFromMarketplace>(answerOffer,
				Application.getRestHeader());

		String url = "http://localhost:8080/consumers/" + currentOffer.getAuthor() + "/offers/" + offer
				+ "/negotiation/" + uuid + "/priceChangeRequest";

		Log.d(uuid, url);

		try {
			rest.exchange(url, HttpMethod.POST, entity, Void.class);
		} catch (Exception e) {
			Log.e(uuid, e.toString());
		}

	}

	/**
	 * Beendet den Verhandlungsstrang des Angebots, in dem der übergebene
	 * Consumer Autor ist. Anschließend wird geprüft, ob beide
	 * Verhandlungsstränge beendet sind. Wenn ja, wird das Ende der Verhandlung
	 * dem Marktplatz gemeldet.
	 * 
	 * @param consumer
	 *            Consumer, dessen Angebot nicht mehr weiter verhandelt wird
	 */
	private void finishNegotiationOffer(UUID consumer) {
		if (consumer.equals(offer1.getAuthor())) {
			finished1 = true;
		} else {
			finished2 = true;
		}
		if (finished1 && finished2) {
			// Teile Marktplatz mit, dass Verhandlung nicht erfolgreich beendet
			// wurde
			informMarketplace(false);
		}
	}

	/**
	 * Behandelt die Antwort eines Consumers mit neuem Preis. Wenn noch eine
	 * Änderung notwendig ist, wird currentSum aktualisiert. Ist currentSum
	 * größer gleich 0, werden die exakten Preise berechnet und die Verhandlung
	 * damit beendet. Ansonsten wird geprüft, ob die maximale Anzahl an
	 * Verhandlungsrunden erreicht ist. Wenn ja erfolgt der Methodenaufruf von
	 * finishNegotiationOffer, wenn nein wird eine erneute Preisanfrage
	 * versendet.
	 * 
	 * @param answer
	 *            antwort des Consumers
	 */
	public void receiveAnswer(AnswerToPriceChangeRequest answer) {
		UUID consumer = answer.getConsumer();
		double newPrice = answer.getNewPrice();
		System.out.println("***receiveAnswer***");
		if (closed) {
			return;
		}
		UUID offer;

		// Berechne, ob sich durch die Anpassung des Preises das akzeptierte
		// Minimum bzw. das aktzeptierte Maximum der Angebotspreise und mit
		// ihnen die mögliche Maximale/Minimale Summe der Angebote geändert hat
		boolean answerFromOffer1 = consumer.equals(offer2.getAuthor());
		if (answerFromOffer1) {
			currentPrice1 = newPrice;
			if (currentPrice1 > acceptedMax1) {
				acceptedMax1 = currentPrice1;
				if (demand1) {
					minCurrentSum = sumLoadprofile1 * acceptedMax1;
				} else {
					maxCurrentSum = sumLoadprofile1 * acceptedMax1;
				}
			}
			if (currentPrice1 < acceptedMin1) {
				acceptedMin1 = currentPrice1;
				if (demand1) {
					maxCurrentSum = sumLoadprofile1 * acceptedMin1;
				} else {
					minCurrentSum = sumLoadprofile1 * acceptedMin1;
				}
			}
			offer = offer1.getUUID();
		} else {
			currentPrice2 = newPrice;
			if (currentPrice2 > acceptedMax2) {
				acceptedMax2 = currentPrice2;
				if (demand2) {
					minCurrentSum = sumLoadprofile2 * acceptedMax2;
				} else {
					maxCurrentSum = sumLoadprofile2 * acceptedMax2;
				}
			}
			if (currentPrice2 < acceptedMin2) {
				acceptedMin2 = currentPrice2;
				if (demand2) {
					maxCurrentSum = sumLoadprofile2 * acceptedMin2;
				} else {
					minCurrentSum = sumLoadprofile2 * acceptedMin2;
				}
			}
			offer = offer2.getUUID();
		}

		// Wenn min den akzeptierten Preisgrenzen eine Einigung möglich ist,
		// berechne die extakten Preise und beende die Verhandlung
		if (minCurrentSum <= 0 && maxCurrentSum >= 0) {
			// Berechne extakte Preise
			if (chargeExactPrices()) {
				// Teile Marktplatz mit, dass Verhandlung erfolgreich beendet
				// wurde
				informMarketplace(true);
				return;
			}
		}

		if (answerFromOffer1 && round1 >= maxRounds || (!answerFromOffer1) && round2 >= maxRounds) {
			finishNegotiationOffer(consumer);
		} else {
			sendPriceRequest(offer);
		}
	}

	/**
	 * Berechnet exakte Preise, wenn Summe >= 0, so dass Summe = 0. Die neuen
	 * Preise werden in currentPrice1 und currentPrice2 gespeichert.
	 * 
	 * @return Array mit neuen Preisen für Offer1 (0) und Offer2 (1)
	 */
	private boolean chargeExactPrices() {
		System.out.println("***chargeExactPrices***");
		// Berechne neue, extakte Preise
		double newPrice1 = sumLoadprofile1 * currentPrice1;
		double newPrice2 = sumLoadprofile2 * currentPrice2;

		if (!(Math.abs(newPrice1) == Math.abs(newPrice2))) {
			if (Math.abs(newPrice1) > Math.abs(newPrice2) && newPrice1 > 0
					|| Math.abs(newPrice1) < Math.abs(newPrice2) && newPrice1 < 0) {
				newPrice1 = Math.abs(newPrice2 / sumLoadprofile1);
				newPrice2 = Math.abs(newPrice2 / sumLoadprofile2);
			} else {
				newPrice2 = Math.abs(newPrice1 / sumLoadprofile2);
				newPrice1 = Math.abs(newPrice1 / sumLoadprofile1);
			}
		}

		// Prüfe, ob Ergebnis auch wirklich im angegebenen Preisrahmen
		if (newPrice1 > acceptedMax1 || newPrice1 < acceptedMin1 || newPrice2 > acceptedMax2
				|| newPrice2 < acceptedMin2) {
			System.out.println("Da ist jetzt was falsch");
			return false;
		} else {
			currentPrice1 = newPrice1;
			currentPrice2 = newPrice2;
			return true;
		}
	}

	/**
	 * Informiert den Marktplatz über den Ausgang der Verhandlung. Diese
	 * Information beinhaltet die UUID der Verhandlung, die neuen Preise der
	 * beiden Angebote und ob die Verhandlung erfolgreich beendet wurde.
	 * 
	 * @param successful
	 *            Gibt an, ob die Verhandlung erfolgreich beendet wurde.
	 */
	private void informMarketplace(boolean successful) {
		// Schließe Verhandlung
		closed = true;

		// Entferne Verhandlung vom Container
		NegotiationContainer container = NegotiationContainer.instance();
		container.delete(uuid);

		// Informiere Marktplatz über Ende der Verhandlung
		EndOfNegotiation end = new EndOfNegotiation(uuid, currentPrice1, currentPrice2, successful);

		RestTemplate rest = new RestTemplate();
		HttpEntity<EndOfNegotiation> entity = new HttpEntity<EndOfNegotiation>(end, Application.getRestHeader());

		String url = "http://localhost:8080/marketplace/endOfNegotiation/";

		Log.d(uuid, url);
		try {
			rest.exchange(url, HttpMethod.POST, entity, Void.class);
		} catch (Exception e) {
			Log.e(uuid, e.toString());
		}
	}
}
