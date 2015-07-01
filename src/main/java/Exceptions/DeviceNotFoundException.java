package Exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such device") // 404
public class DeviceNotFoundException extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
