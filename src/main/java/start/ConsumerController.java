package start;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;
import com.wordnik.swagger.annotations.ApiOperation;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Loadprofile;
import Entity.Marketplace;
import Entity.Offer;
import Packet.OfferNotification;
import Util.ResponseBuilder;
import Util.View;
import Packet.AnswerChangeRequestLoadprofile;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;

@RestController
@RequestMapping(value = Application.Params.VERSION)
public class ConsumerController {

	/**
	 * Gibt alle Consumer als JSON in der Summary-Ansicht zurueck
	 * 
	 * @return Consumer[] alle angelegten Consumer
	 */
	@ApiOperation(value = "Gibt ale Consumer zurück")
	@JsonView(View.Summary.class)
	@RequestMapping(value = "/consumers", method = RequestMethod.GET)
	public ResponseEntity<Consumer[]> getAllConsumers() {
		return new ResponseBuilder<Consumer[]>(Marketplace.instance()).body(ConsumerContainer.instance().getAll())
				.build();
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
	 * @param deviceUUID
	 *            das zu verbindende Gerät
	 * @return Leere Antwort
	 */
	@RequestMapping(value = "/consumers/{consumerUUID}/link/{deviceUUID}", method = RequestMethod.POST)
	public ResponseEntity<Void> linkDeviceToCustomer(@PathVariable UUID consumerUUID, @PathVariable UUID deviceUUID) {
		ConsumerContainer.instance().get(consumerUUID).setDevice(deviceUUID);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(consumerUUID));
	}

	/**
	 * Gibt alle Angebote eines Consumers als JSON zurueck
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @return Offer[]
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.GET)
	public ResponseEntity<Offer[]> getAllOffers(@PathVariable UUID uuid) {
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
	public ResponseEntity<Offer> getOffer(@PathVariable UUID uuid, @PathVariable UUID uuidOffer) {
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
	 * @param identity
	 *            Wer ruft
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/loadprofiles", method = RequestMethod.POST)
	public ResponseEntity<Void> receiveLoadprofileByDevice(
			@RequestHeader(value = "UUID", required = true) String identity, @RequestBody Loadprofile loadprofile,
			@PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveLoadprofile(loadprofile);
		return new ResponseBuilder<Void>(ConsumerContainer.instance().get(uuid)).build();
	}

	/**
	 * Consumer erhaelt eine Benachrichtigung zu einem neuen Angebot eines
	 * anderen Consumers
	 * 
	 * @param offerNotification
	 *            Angebotsbenachrichtigung
	 * @param uuid
	 *            Consumer-ID
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.POST)
	public ResponseEntity<Void> receiveOfferNotification(@RequestBody OfferNotification offerNotification,
			@PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveOfferNotification(offerNotification);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
	}

	/**
	 * Ein Angebot wir durch den Marktplatz bestaetigt
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param answerOffer
	 *            Angebots-Antwort
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/confirmByMarketplace", method = RequestMethod.POST)
	public ResponseEntity<Void> confirmOfferByMarketplace(@PathVariable UUID uuid,
			@RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).confirmOfferByMarketplace(answerOffer);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));

	}

	/**
	 * Consumer erhaelt eine Aenderungsaufforderung des Lastprofils vom
	 * Marktplatz
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param cr
	 *            Aenderungsaufforderung
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/changeRequestMarketplace", method = RequestMethod.POST)
	public ResponseEntity<Void> receiveChangeRequestLoadprofile(@PathVariable UUID uuid,
			@RequestBody ChangeRequestLoadprofile cr) {
		ConsumerContainer.instance().get(uuid).receiveChangeRequestLoadprofileFromMarketplace(cr);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));

	}

	/**
	 * Consumer erhaelt eine Aenderungsaufforderung des Lastprofils von einem
	 * anderen Consumer
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param cr
	 *            Aenderungsaufforderung
	 * @return Antwort auf CR
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/changeRequestConsumer", method = RequestMethod.POST)
	public ResponseEntity<AnswerChangeRequestLoadprofile> receiveChangeRequest(@PathVariable UUID uuid,
			@RequestBody ChangeRequestLoadprofile cr) {
		AnswerChangeRequestLoadprofile answer = ConsumerContainer.instance().get(uuid)
				.receiveChangeRequestLoadprofile(cr);
		return new ResponseBuilder<AnswerChangeRequestLoadprofile>(ConsumerContainer.instance().get(uuid)).body(answer)
				.build();
	}

	/**
	 * Consumer erhält eine Antwort auf eine Aenderungsaufforderung des
	 * Lastprofils
	 * 
	 * @param uuid
	 *            Consumer-ID
	 * @param cr
	 *            Aenderungsaufforderung
	 * @return Antwort auf CR
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/answerChangeRequest", method = RequestMethod.POST)
	public ResponseEntity<Void> answerChangeRequest(@PathVariable UUID uuid, @PathVariable UUID uuidOffer,
			@RequestBody AnswerChangeRequestLoadprofile cr) {
		ConsumerContainer.instance().get(uuid).receiveAnswerChangeLoadprofile(cr, uuidOffer);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
	}

	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/changeRequest/decline", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveChangeRequestDecline(@PathVariable UUID uuid, @PathVariable UUID uuidOffer) {
		ConsumerContainer.instance().get(uuid).receiveChangeRequestDecline(uuidOffer);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
	}

	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/changeRequest/confirm", method = RequestMethod.GET)
	public ResponseEntity<Void> receiveChangeRequestConfirmation(@PathVariable UUID uuid,
			@PathVariable UUID uuidOffer) {
		ConsumerContainer.instance().get(uuid).receiveChangeRequestConfirm(uuidOffer);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
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
	 * @return Leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/negotiation/{uuidNegotiation}/priceChangeRequest", method = RequestMethod.POST)
	public ResponseEntity<Void> priceChangeRequest(@PathVariable UUID uuid, @PathVariable UUID uuidNegotiation,
			@RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).priceChangeRequest(answerOffer, uuidNegotiation);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
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
	public ResponseEntity<Boolean> confirmOfferByConsumer(@PathVariable UUID uuidConsumer, @PathVariable UUID uuidOffer,
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
	 * @return leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOfferOld}/replace/{uuidOfferNew}", method = RequestMethod.GET)
	public ResponseEntity<Void> replaceOffer(@PathVariable UUID uuidConsumer, @PathVariable UUID uuidOfferOld,
			@PathVariable UUID uuidOfferNew) {
		ConsumerContainer.instance().get(uuidConsumer).replaceOffer(uuidOfferOld, uuidOfferNew);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuidConsumer));
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
	 * @return Leere Antwort
	 */
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/answer", method = RequestMethod.POST)
	public ResponseEntity<Void> confirmOfferByDevice(@PathVariable UUID uuid, @PathVariable UUID uuidOffer,
			@RequestBody OfferNotification offerNotification) {
		ConsumerContainer.instance().get(uuid).answerOffer(uuidOffer, offerNotification);
		return ResponseBuilder.returnVoid(ConsumerContainer.instance().get(uuid));
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
