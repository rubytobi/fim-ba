package start;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;


import Packet.AnswerToPriceChangeRequest;
import Container.NegotiationContainer;
import Util.Negotiation;
import Util.ResponseBuilder;

@RestController
@RequestMapping(value = Application.Params.VERSION)
public class NegotiationController {

	@RequestMapping(value = "/negotiation/{uuid}/answerToPriceChangeRequest", method = RequestMethod.POST)
	public ResponseEntity<Void> postDemand(@PathVariable UUID uuid, @RequestBody AnswerToPriceChangeRequest answer) {
		System.out.println("UUID: " +uuid);
		Negotiation negotiation = NegotiationContainer.instance().get(uuid);
		// Sende Antwort nur, wenn Negotiation noch existiert und noch nicht
		// geschlossen wurde
		if (negotiation != null) {
			negotiation.receiveAnswer(answer);
		}
		else {
			System.out.println("Negotiation ist null");
		}
		return ResponseBuilder.returnVoid(negotiation);
	}
	
	@RequestMapping(value = "/negotiations", method = RequestMethod.GET)
	public Negotiation[] getAllDevices() {
		return NegotiationContainer.instance().getAll();
	}
}
