package start;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import Container.ConsumerContainer;
import Container.DeviceContainer;
import Entity.Consumer;
import Entity.Offer;
import Event.InvalidOffer;
import Packet.DeviceLoadprofile;
import Packet.OfferNotification;

@RestController
public class ConsumerController {

	@RequestMapping(value = "/consumers", method = RequestMethod.GET)
	public Device[] getAllConsumers() {
		return DeviceContainer.instance().getAll();
	}

	@RequestMapping(value = "/consumers", method = RequestMethod.POST)
	public UUID addConsumer(@RequestBody Consumer consumer) {
		ConsumerContainer.instance().add(consumer);
		return consumer.getUUID();
	}

	@RequestMapping(value = "/consumers/{uuid}", method = RequestMethod.GET)
	public Consumer getSingleDevice(@PathVariable UUID uuid) {
		return ConsumerContainer.instance().get(uuid);
	}

	@RequestMapping(value = "/consumers/{uuid}", method = RequestMethod.POST)
	public void receiveLoadprofile(@PathVariable UUID uuid, @RequestBody DeviceLoadprofile loadprofile) {
		ConsumerContainer.instance().get(uuid).loadprofile(loadprofile);
	}

	@RequestMapping(value = "/consumers/{uuid}/offers", method = RequestMethod.POST)
	public void postOffer(@RequestBody OfferNotification offerNotification, @PathVariable UUID uuid) {
		ConsumerContainer.instance().get(uuid).receiveOfferNotification(offerNotification);
	}

	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}", method = RequestMethod.GET)
	public Offer getOffer(@PathVariable UUID uuidConsumer, UUID uuidOffer) {
		return ConsumerContainer.instance().get(uuidConsumer).getOffer(uuidOffer);
	}

	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}/status", method = RequestMethod.GET)
	public Map<String, Object> getOfferStatus(@PathVariable UUID uuidConsumer, UUID uuidOffer) {
		return ConsumerContainer.instance().get(uuidConsumer).getOffer(uuidOffer).status();
	}

	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}/confirm", method = RequestMethod.GET)
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void confirmOffer(@PathVariable UUID uuidConsumer, UUID uuidOffer) {
		if (ConsumerContainer.instance().get(uuidConsumer).confirmOffer(uuidOffer)) {
			throw new InvalidOffer();
		}
	}

	@RequestMapping(value = "/consumers/{uuidConsumer}/offers/{uuidOffer}/cancel", method = RequestMethod.GET)
	public void cancelOffer(@PathVariable UUID uuidConsumer, UUID uuidOffer) {
		ConsumerContainer.instance().get(uuidConsumer).cancelOffer(uuidOffer);
	}
}
