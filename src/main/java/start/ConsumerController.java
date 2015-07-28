package start;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Offer;
import Packet.OfferNotification;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;

@RestController
public class ConsumerController {

	/**
	 * Gibt alle Consumer als JSON in der Summary-Ansicht zurueck
	 * 
	 * @return Consumer[] alle angelegten Consumer
	 */
	@JsonView(View.Summary.class)
	@RequestMapping(value = "/consumers", method = RequestMethod.GET)
	public Consumer[] getAllConsumers() {
		return ConsumerContainer.instance().getAll();
	}

	/**
	 * Anlegen eines neuen Consumers
	 * 
	 * @return UUID die neue ID des Consumers
	 */
	@RequestMapping(value = "/consumers", method = RequestMethod.POST)
	public UUID addConsumer() {
		Consumer consumer = new Consumer();
		ConsumerContainer.instance().add(consumer);
		return consumer.getUUID();
	}

	/**
	 * Rueckgabe eines einzelnen Consumers
	 * 
	 * @param uuid
	 *            spezifiziert den einzelnen Consumer
	 * @return Consumer
	 */
	@RequestMapping(value = "/consumers/{uuid}", method = RequestMethod.GET)
	public Consumer getSingleConsumer(@PathVariable UUID uuid) {
		return ConsumerContainer.instance().get(uuid);
	}

	/**
	 * Verbindet einen Consumer mit einem Device
	 * 
	 * @param consumerUUID
	 *            der gewuenschte Consumer
	 * @param fridgeUUID
	 *            das zu verbindende Gerät
	 */
	@RequestMapping(value = "/consumers/{consumerUUID}/link/{fridgeUUID}", method = RequestMethod.POST)
	public void linkDeviceToCustomer(@PathVariable UUID consumerUUID, @PathVariable UUID fridgeUUID) {
		ConsumerContainer.instance().get(consumerUUID).setDevice(fridgeUUID);
	}

	/**
	 * Gibt alle Angebote eines Consumers als JSON zurueck
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @return Offer[]
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.GET)
	public Offer[] getAllOffers(@PathVariable UUID uuid) {
		return ConsumerContainer.instance().get(uuid).getAllOffers();
	}

	/**
	 * Gibt ein spezifisches Angebot eines Consumers zurueck
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param uuidOffer
	 *            Angebots-ID
	 * @return Offer
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}", method = RequestMethod.GET)
	public Offer getOffer(@PathVariable UUID uuid, @PathVariable UUID uuidOffer) {
		return ConsumerContainer.instance().get(uuid).getOffer(uuidOffer);
	}

	/**
	 * Versetzt dem Consumer einen regelmaessigen stupser (ping)
	 * 
	 * @param uuid
	 *            Consumer-ID
	 */
	@RequestMapping(value = "/consumers/{uuid}/ping", method = RequestMethod.GET)
	public void ping(@PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).ping();
	}

	/**
	 * Uebergibt dem Consumer ein neues Lastprofil durch das Device
	 * 
	 * @param loadprofile
	 *            Lastprofil
	 * @param uuid
	 *            Consumer-ID
	 */
	@RequestMapping(value = "/consumers/{uuid}/loadprofiles", method = RequestMethod.POST)
	public void receiveLoadprofileByDevice(@RequestBody Loadprofile loadprofile, @PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveLoadprofile(loadprofile);
	}

	/**
	 * Consumer erhaelt eine Benachrichtigung zu einem neuen Angebot eines
	 * anderen Consumers
	 * 
	 * @param offerNotification
	 *            Angebotsbenachrichtigung
	 * @param uuid
	 *            Consumer-ID
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.POST)
	public void receiveOfferNotification(@RequestBody OfferNotification offerNotification, @PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveOfferNotification(offerNotification);
	}

	/**
	 * Ein Angebot wir durch den Marktplatz bestaetigt
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param answerOffer
	 *            Angebots-Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/confirmByMarketplace", method = RequestMethod.POST)
	public void confirmOfferByMarketplace(@PathVariable UUID uuid,
			@RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).confirmOfferByMarketplace(answerOffer);
	}

	/**
	 * Consumer erhaelt eine Aenderungsaufforderung des Lastprofils
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param cr
	 *            Aenderungsaufforderung
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/receiveChangeRequestLoadprofile", method = RequestMethod.POST)
	public void receiveChangeRequestLoadprofile(@PathVariable UUID uuid, @RequestBody ChangeRequestLoadprofile cr) {
		ConsumerContainer.instance().get(uuid).receiveChangeRequestLoadprofile(cr);
	}

	/**
	 * Consumer erhaelt eine Aufforderung zum Preis aendern eines Angebots
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param uuidNegotiation
	 *            Verhandlungs-ID
	 * @param answerOffer
	 *            Angebots-Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/negotiation/{uuidNegotiation/priceChangeRequest", method = RequestMethod.POST)
	public void priceChangeRequest(@PathVariable UUID uuid, @PathVariable UUID uuidNegotiation,
			@RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).priceChangeRequest(answerOffer, uuidNegotiation);
	}

	/**
	 * Consumer erhaelt eine bestaetigung eines Angebots durch einen anderen
	 * Consumer
	 * 
	 * @param uuidConsumer
	 *            Consumer-ID
	 * @param uuidOffer
	 *            Angebots-ID
	 * @param key
	 *            Angebotsschluessel
	 * @return Consumer bestaetigt Bestaetigung
	 */
	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}/confirm/{key}", method = RequestMethod.GET)
	public boolean confirmOfferByConsumer(@PathVariable UUID uuidConsumer, @PathVariable UUID uuidOffer,
			@PathVariable UUID key) {
		return ConsumerContainer.instance().get(uuidConsumer).confirmOfferByConsumer(uuidOffer, key);
	}

	/**
	 * Ein Mitglied bekommt durch seinen Chef im Angebot ein erneuertes
	 * Übermittelt
	 * 
	 * @param uuidConsumer
	 *            betroffener Consumer
	 * @param uuidOfferOld
	 *            alte Angebots-ID
	 * @param uuidOfferNew
	 *            neue Angebots-ID
	 */
	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOfferOld}/replace/{uuidOfferNew}", method = RequestMethod.GET)
	public void replaceOffer(@PathVariable UUID uuidConsumer, @PathVariable UUID uuidOfferOld,
			@PathVariable UUID uuidOfferNew) {
		ConsumerContainer.instance().get(uuidConsumer).replaceOffer(uuidOfferOld, uuidOfferNew);
	}

	/**
	 * Ein Consumer erhielt ein Angebot, befand dies für gut und antwortet nun
	 * darauf mit seinem um das eigene Lastprofil erweiterte Angebot
	 * 
	 * @param uuid
	 *            der anzusprechende Consumer
	 * @param uuidOffer
	 *            Angebots-ID auf die geantwortet wird
	 * @param offerNotification
	 *            Antgebotsbenachrichtigung
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/answer", method = RequestMethod.POST)
	public void confirmOfferByDevice(@PathVariable UUID uuid, @PathVariable UUID uuidOffer,
			@RequestBody OfferNotification offerNotification) {
		ConsumerContainer.instance().get(uuid).answerOffer(uuidOffer, offerNotification);
	}

	/**
	 * Consumer erhaelt eine Benachrichtigung ueber eine Absage auf ein Angebot
	 * 
	 * @param uuid
	 *            consumer-ID
	 * @param uuidOffer
	 *            Angebots-ID
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/cancel", method = RequestMethod.GET)
	public void cancelOffer(@PathVariable UUID uuid, UUID uuidOffer) {
		ConsumerContainer.instance().get(uuid).cancelOffer(uuidOffer);
	}
}
