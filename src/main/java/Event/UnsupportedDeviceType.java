package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNPROCESSABLE_ENTITY, reason = "Devicetype not supported") // 404
public class UnsupportedDeviceType extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
