package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY, reason = "Insufficient information.") // 404
public class IllegalDeviceCreation extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
