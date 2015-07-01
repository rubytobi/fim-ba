package start;

import java.util.Date;

import org.apache.tomcat.jni.Time;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GeneralController {

	@RequestMapping("/index")
	public String index() {
		Date date = new Date();
		Time time = new Time();

		return date.toString() + " " + time.toString();
	}
}
