package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT, reason = "Contract not possible") // 404
public class DeclineContract extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
