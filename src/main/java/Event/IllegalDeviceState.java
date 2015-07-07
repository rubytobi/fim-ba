package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY, reason = "Cannot set consumer at uninitilized device.") // 404
public class IllegalDeviceState extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
