package start;

import java.util.HashMap;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Container.ConsumerContainer;
import Entity.Consumer;
import Entity.Offer;

@RestController
public class GeneralController {

	@RequestMapping("/index")
	public HashMap<UUID, Offer> index() {
		HashMap<UUID, Offer> map = new HashMap<UUID, Offer>();

		for (Consumer c : ConsumerContainer.instance().getAll()) {
			map.put(c.getOffer().getUUID(), c.getOffer());
		}

		return map;
	}
}
