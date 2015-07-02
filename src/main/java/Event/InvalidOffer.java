package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY, reason = "Offer has been invalidated...")
public class InvalidOffer extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
