package start;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonView;

import Container.ConsumerContainer;
import Container.DeviceContainer;
import Entity.Consumer;
import Entity.Offer;
import Event.InvalidOffer;
import Packet.FridgeCreation;
import Packet.OfferNotification;
import Packet.AnswerToOfferFromMarketplace;
import Packet.ChangeRequestLoadprofile;

@RestController
public class ConsumerController {

	/**
	 * Alle Consumer als JSON in der Summary Ansicht
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
	 * Rückgabe eines einzelnen Consumers
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
	 *            der gewünschte Consumer
	 * @param fridgeUUID
	 *            das zu verbindende Gerät
	 */
	@RequestMapping(value = "/consumers/{consumerUUID}/link/{fridgeUUID}", method = RequestMethod.POST)
	public void linkDeviceToCustomer(@PathVariable UUID consumerUUID, @PathVariable UUID fridgeUUID) {
		ConsumerContainer.instance().get(consumerUUID).setDevice(fridgeUUID);
	}

	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.GET)
	public Offer[] getAllOffers(@PathVariable UUID uuid) {
		return ConsumerContainer.instance().get(uuid).getAllOffers();
	}

	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}", method = RequestMethod.GET)
	public Offer getOffer(@PathVariable UUID uuid, @PathVariable UUID uuidOffer) {
		return ConsumerContainer.instance().get(uuid).getOffer(uuidOffer);
	}

	@RequestMapping(value = "/consumers/{uuid}/ping", method = RequestMethod.GET)
	public void ping(@PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).ping();
	}

	@RequestMapping(value = "/consumers/{uuid}/loadprofiles", method = RequestMethod.POST)
	public boolean receiveLoadprofileByDevice(@RequestBody Loadprofile loadprofile, @PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveLoadprofile(loadprofile);
		return true;
	}

	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.POST)
	public void receiveOfferNotification(@RequestBody OfferNotification offerNotification, @PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveOfferNotification(offerNotification);
	}

	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/confirmByMarketplace", method = RequestMethod.POST)
	public void confirmOfferByMarketplace(@PathVariable UUID uuid, @RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).confirmOfferByMarketplace(answerOffer);
	}
	
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/receiveChangeRequestLoadprofile", method = RequestMethod.POST)
	public void receiveChangeRequestLoadprofile(@PathVariable UUID uuid, @RequestBody ChangeRequestLoadprofile cr) {
		ConsumerContainer.instance().get(uuid).receiveChangeRequestLoadprofile(cr);
	}
	
	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/negotiation/{uuidNegotiation/priceChangeRequest", method = RequestMethod.POST)
	public void priceChangeRequest(@PathVariable UUID uuid, @PathVariable UUID uuidNegotiation, @RequestBody AnswerToOfferFromMarketplace answerOffer) {
		ConsumerContainer.instance().get(uuid).priceChangeRequest(answerOffer, uuidNegotiation);
	}

	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}/confirm/{uuidKey}", method = RequestMethod.GET)
	public boolean confirmOfferByConsumer(@PathVariable UUID uuidConsumer, @PathVariable UUID uuidOffer,
			@PathVariable UUID uuidKey) {
		return ConsumerContainer.instance().get(uuidConsumer).confirmOfferByConsumer(uuidOffer, uuidKey);
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
			@PathVariable UUID uuidOfferNew, @RequestHeader UUID author) {
		ConsumerContainer.instance().get(uuidConsumer).replaceOffer(uuidOfferOld, uuidOfferNew, author);
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

	@RequestMapping(value = "/consumers/{uuid}/offers/{uuidOffer}/cancel", method = RequestMethod.GET)
	public void cancelOffer(@PathVariable UUID uuid, UUID uuidOffer) {
		ConsumerContainer.instance().get(uuid).cancelOffer(uuidOffer);
	}
}
