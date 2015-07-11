package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.OK, reason = "Offer has been accepted") // 404
public class OfferAccepted extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
