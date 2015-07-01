package start;

import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsumerController {

	@RequestMapping(value = "/consumers", method = RequestMethod.GET)
	public Device[] getAllConsumers() {
		return DeviceContainer.instance().getAll();
	}

	@RequestMapping(value = "/consumers", method = RequestMethod.PUT)
	public boolean addConsumer() {
		Consumer consumer = new Consumer("Tobi Ruby");
		ConsumerContainer.instance().add(consumer);
		return true;
	}

	@RequestMapping(value = "/consumers/{id}", method = RequestMethod.GET)
	public Consumer getSingleDevice(@PathVariable UUID uuid) {
		return ConsumerContainer.instance().get(uuid);
	}

}
