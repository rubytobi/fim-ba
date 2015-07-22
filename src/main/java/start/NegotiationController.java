package start;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

import Packet.AnswerToPriceChangeRequest;
import Container.NegotiationContainer;

public class NegotiationController {
	@RequestMapping(value = "/negotiation/{uuid}/answerToPriceChangeRequest", method = RequestMethod.POST)
	public void postDemand(@PathVariable UUID uuid, @RequestBody AnswerToPriceChangeRequest answer) {
		NegotiationContainer.instance().get(uuid).receiveAnswer(answer);
	}
}
