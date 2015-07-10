package Event;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.ACCEPTED, reason = "Contract accepted") // 404
public class AcceptContract extends RuntimeException {
	private static final long serialVersionUID = 1L;
}
